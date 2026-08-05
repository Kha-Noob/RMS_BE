package web.restaurant.swp.modules.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import web.restaurant.swp.modules.branch.model.Branch;
import web.restaurant.swp.modules.branch.repository.BranchRepository;
import web.restaurant.swp.modules.review.model.CustomerReview;
import web.restaurant.swp.modules.review.repository.CustomerReviewRepository;
import web.restaurant.swp.modules.inventory.model.Product;
import web.restaurant.swp.modules.inventory.model.ProductVariant;
import web.restaurant.swp.modules.inventory.repository.ProductRepository;
import web.restaurant.swp.modules.inventory.repository.ProductVariantRepository;
import web.restaurant.swp.modules.pos.model.TableEntity;
import web.restaurant.swp.modules.pos.repository.TableRepository;

import web.restaurant.swp.modules.analytics.controller.AIChatController.ChatRequest.HistoryMessage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIChatService {

    private final BranchRepository branchRepository;
    private final CustomerReviewRepository customerReviewRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final TableRepository tableRepository;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api.key:}")
    private String apiKey;

    // --- NFC normalization helper ---
    private static String nfc(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFC);
    }

    public String getChatResponse(String message, Double userLat, Double userLng) {
        return getChatResponse(message, userLat, userLng, null, null);
    }

    public String getChatResponse(String message, Double userLat, Double userLng, String tenantId) {
        return getChatResponse(message, userLat, userLng, tenantId, null);
    }

    public String getChatResponse(String message, Double userLat, Double userLng, String tenantId, List<HistoryMessage> history) {
        // 1. Fetch active branches
        List<Branch> branches;
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            branches = branchRepository.findByTenantTenantIdAndIsActiveTrue(tenantId);
        } else {
            branches = branchRepository.findAllByIsActiveTrue();
        }

        // 2. Fetch all reviews and calculate average ratings
        List<CustomerReview> reviews = customerReviewRepository.findAll();
        Map<String, List<CustomerReview>> reviewsByBranch = reviews.stream()
                .collect(Collectors.groupingBy(CustomerReview::getBranchId));

        // 3. Fetch menu products and variants
        List<Product> products;
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            products = productRepository.findByTenantTenantIdAndIsActiveTrue(tenantId);
        } else {
            products = productRepository.findByIsActiveTrue();
        }
        List<ProductVariant> variants = productVariantRepository.findAll();
        Map<Long, List<ProductVariant>> variantsByProduct = variants.stream()
                .collect(Collectors.groupingBy(pv -> pv.getProduct().getId()));

        // 4. Fetch tables and group by branch
        List<TableEntity> tables = tableRepository.findAll();
        Map<String, List<TableEntity>> tablesByBranch = tables.stream()
                .filter(t -> t.getRoom() != null && t.getRoom().getBranch() != null)
                .collect(Collectors.groupingBy(t -> t.getRoom().getBranch().getBranchId()));

        // --- Build Restaurant Branches Context ---
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("Dữ liệu các chi nhánh nhà hàng trên nền tảng (LiteFlow):\n");

        Branch nearestBranch = null;
        double minDistance = Double.MAX_VALUE;

        Branch highestRatedBranch = null;
        double maxRating = -1.0;

        for (Branch b : branches) {
            List<CustomerReview> branchReviews = reviewsByBranch.get(b.getBranchId());
            double avgRating = 0.0;
            int reviewCount = 0;
            if (branchReviews != null && !branchReviews.isEmpty()) {
                avgRating = branchReviews.stream().mapToDouble(CustomerReview::getRating).average().orElse(0.0);
                reviewCount = branchReviews.size();
            }

            Double distance = null;
            if (userLat != null && userLng != null && b.getLatitude() != null && b.getLongitude() != null) {
                distance = calculateDistance(userLat, userLng, b.getLatitude(), b.getLongitude());
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestBranch = b;
                }
            }

            if (avgRating > maxRating) {
                maxRating = avgRating;
                highestRatedBranch = b;
            }

            List<TableEntity> branchTables = tablesByBranch.get(b.getBranchId());
            long vacantCount = 0;
            long totalTables = 0;
            if (branchTables != null) {
                totalTables = branchTables.size();
                vacantCount = branchTables.stream().filter(t -> "EMPTY".equalsIgnoreCase(t.getStatus())).count();
            }

            contextBuilder.append("- Tên chi nhánh: ").append(b.getName()).append("\n")
                    .append("  Mã chi nhánh (branchId): ").append(b.getBranchId()).append("\n")
                    .append("  Địa chỉ: ").append(b.getAddress()).append("\n")
                    .append("  Điện thoại: ").append(b.getPhone()).append("\n")
                    .append("  Đánh giá trung bình: ").append(reviewCount > 0 ? String.format("%.1f", avgRating) : "Chưa có đánh giá")
                    .append(" (từ ").append(reviewCount).append(" lượt đánh giá)\n")
                    .append("  Bàn trống hiện tại: ").append(vacantCount).append(" bàn trống / ").append(totalTables).append(" bàn tổng số\n");
            
            if (branchTables != null && vacantCount > 0) {
                contextBuilder.append("  Danh sách bàn trống cụ thể:\n");
                for (TableEntity t : branchTables) {
                    if ("EMPTY".equalsIgnoreCase(t.getStatus())) {
                        contextBuilder.append("    * Bàn: ").append(t.getName())
                                      .append(" (Sức chứa: ").append(t.getCapacity()).append(" khách) tại khu vực ")
                                      .append(t.getRoom().getName()).append("\n");
                    }
                }
            }

            if (distance != null) {
                contextBuilder.append("  Khoảng cách đến vị trí hiện tại của khách hàng: ").append(String.format("%.2f", distance)).append(" km\n");
            } else {
                contextBuilder.append("  Khoảng cách đến khách hàng: Chưa chia sẻ vị trí\n");
            }
            contextBuilder.append("\n");
        }

        // --- Build Menu / Products Context ---
        StringBuilder menuBuilder = new StringBuilder();
        menuBuilder.append("Thực đơn / Menu các món ăn trên toàn hệ thống:\n");
        for (Product p : products) {
            menuBuilder.append("- Tên món: ").append(p.getName()).append("\n")
                    .append("  Mô tả: ").append(p.getDescription() != null ? p.getDescription() : "Không có mô tả").append("\n")
                    .append("  Thành phần: ").append(p.getIngredients() != null ? p.getIngredients() : "Không ghi rõ").append("\n")
                    .append("  Phân loại (Category): ").append(p.getCategory() != null ? p.getCategory().getName() : "Món ăn").append("\n");
            
            List<ProductVariant> pVariants = variantsByProduct.get(p.getId());
            if (pVariants != null && !pVariants.isEmpty()) {
                menuBuilder.append("  Các kích cỡ / biến thể & giá bán:\n");
                for (ProductVariant pv : pVariants) {
                    menuBuilder.append("    * ").append(pv.getName()).append(" - Giá: ").append(String.format("%,.0f", pv.getPrice())).append(" VNĐ");
                    if (pv.getBranchId() != null) {
                        menuBuilder.append(" (Chỉ phục vụ tại chi nhánh mã: ").append(pv.getBranchId()).append(")");
                    } else {
                        menuBuilder.append(" (Áp dụng tại tất cả các chi nhánh)");
                    }
                    menuBuilder.append("\n");
                }
            }
            menuBuilder.append("\n");
        }

        // 5. Try to call Gemini API if key is available
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            try {
                String systemPrompt = "Bạn là trợ lý ảo AI thông minh, thân thiện của chuỗi nhà hàng LiteFlow.\n"
                        + "Nhiệm vụ của bạn là hỗ trợ khách hàng giải đáp mọi thắc mắc về thực đơn, giá cả, bàn trống, đánh giá và định vị chi nhánh gần nhất.\n"
                        + "Hãy trả lời một cách tự nhiên, sinh động, ngắn gọn và chính xác bằng tiếng Việt dựa vào dữ liệu thực tế hệ thống dưới đây:\n\n"
                        + contextBuilder.toString() + "\n"
                        + menuBuilder.toString()
                        + "\nLưu ý quan trọng:\n"
                        + "- Khi khách hỏi món ăn cụ thể, hãy đối chiếu với Menu để xem nhà hàng nào có món đó. Nếu món ăn ghi \"Áp dụng tại tất cả các chi nhánh\", nghĩa là khách có thể ăn ở bất kỳ chi nhánh nào.\n"
                        + "- Khi khách hỏi về bàn trống, hãy kiểm tra tình trạng bàn trống thực tế của các chi nhánh và trả lời chi tiết chi nhánh đó đang trống bao nhiêu bàn, tên bàn là gì.\n"
                        + "- Khi khách hàng muốn đặt bàn hoặc hỏi cách đặt bàn, hãy cung cấp liên kết Markdown trong câu trả lời dạng: [Đặt bàn tại <Tên Chi nhánh>](/booking?branchId=<Mã chi nhánh>) để họ nhấp vào đặt trực tuyến. Điền sẵn branchId của chi nhánh khách hàng đang đề cập hoặc chi nhánh gần họ nhất.\n"
                        + "- Trả lời tự nhiên, tránh lặp khuôn hay cứng nhắc. Dùng các câu từ hiếu khách.\n";

                log.info("Built System Prompt for Gemini (length={})", systemPrompt.length());

                // Build multi-turn contents array with strict user-model alternation starting with user
                List<HistoryMessage> validHistory = new ArrayList<>();
                if (history != null && !history.isEmpty()) {
                    for (HistoryMessage h : history) {
                        if (h.getText() == null || h.getText().trim().isEmpty()) continue;
                        String role = "model".equalsIgnoreCase(h.getRole()) ? "model" : "user";
                        if (validHistory.isEmpty()) {
                            if ("user".equals(role)) {
                                validHistory.add(h);
                            }
                        } else {
                            String lastRole = "model".equalsIgnoreCase(validHistory.get(validHistory.size() - 1).getRole()) ? "model" : "user";
                            if (!role.equals(lastRole)) {
                                validHistory.add(h);
                            }
                        }
                    }
                    // If last item is user, remove it (current message will be appended separately)
                    if (!validHistory.isEmpty() && "user".equalsIgnoreCase(validHistory.get(validHistory.size() - 1).getRole())) {
                        validHistory.remove(validHistory.size() - 1);
                    }
                    // Cap to max 6 items, ensure starts with user
                    if (validHistory.size() > 6) {
                        validHistory = new ArrayList<>(validHistory.subList(validHistory.size() - 6, validHistory.size()));
                    }
                    if (!validHistory.isEmpty() && "model".equalsIgnoreCase(validHistory.get(0).getRole())) {
                        validHistory.remove(0);
                    }
                }

                // Build contents array using ObjectMapper for safe JSON serialization (Fix Issue #8)
                List<Map<String, Object>> contentsArray = new ArrayList<>();
                for (HistoryMessage h : validHistory) {
                    String role = "model".equalsIgnoreCase(h.getRole()) ? "model" : "user";
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("role", role);
                    entry.put("parts", List.of(Map.of("text", h.getText())));
                    contentsArray.add(entry);
                }
                // Append current user message
                Map<String, Object> currentMsg = new LinkedHashMap<>();
                currentMsg.put("role", "user");
                currentMsg.put("parts", List.of(Map.of("text", message)));
                contentsArray.add(currentMsg);

                // Build full request body using ObjectMapper
                Map<String, Object> requestBodyMap = new LinkedHashMap<>();
                requestBodyMap.put("system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
                requestBodyMap.put("contents", contentsArray);

                String requestBody = objectMapper.writeValueAsString(requestBodyMap);

                HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(15))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=" + apiKey))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    return root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
                } else {
                    log.warn("Gemini API returned status code: {} - Body: {}", response.statusCode(), response.body());
                }
            } catch (Exception e) {
                log.error("Failed to generate AI response, falling back to rule-based response", e);
            }
        }

        // 6. Fallback Rule-Based Engine (all string comparisons use NFC-normalized text)
        String cleanMsg = nfc(message.toLowerCase());

        // --- Determine intent flags to handle combined queries (Fix Bug #3) ---
        boolean wantsProximity = cleanMsg.contains("gần nhất") || cleanMsg.contains("gần đây") || cleanMsg.contains("gần tôi")
                || cleanMsg.contains("định vị") || cleanMsg.contains("location") || cleanMsg.contains("chỉ đường");
        Product mentionedProduct = findProductInText(cleanMsg, products);
        if (mentionedProduct == null) {
            mentionedProduct = findProductSoft(cleanMsg, products);
        }
        boolean wantsFood = mentionedProduct != null;

        // 6.1 Combined query: proximity + food (e.g. "chi nhánh nào gần tôi có cơm tấm") (Fix Bug #3)
        if (wantsProximity && wantsFood) {
            StringBuilder combo = new StringBuilder();
            combo.append(String.format("LiteFlow có món **%s** mà bạn tìm kiếm!\n", mentionedProduct.getName()));
            List<ProductVariant> pVariants = variantsByProduct.get(mentionedProduct.getId());
            if (pVariants != null && !pVariants.isEmpty()) {
                combo.append("   *Giá bán:* ");
                List<String> varStrings = new ArrayList<>();
                for (ProductVariant pv : pVariants) {
                    varStrings.add(String.format("%,.0f VNĐ (%s)", pv.getPrice(), pv.getName()));
                }
                combo.append(String.join(", ", varStrings)).append("\n\n");
            }
            if (nearestBranch != null && minDistance != Double.MAX_VALUE) {
                combo.append(String.format("Chi nhánh gần bạn nhất là **%s** tại **%s** (cách bạn khoảng **%.2f km**). ",
                        nearestBranch.getName(), nearestBranch.getAddress(), minDistance));
                combo.append(String.format("[Đặt bàn tại %s](/booking?branchId=%s)", nearestBranch.getName(), nearestBranch.getBranchId()));
            } else if (userLat != null && userLng != null) {
                combo.append("Đã nhận tọa độ của bạn nhưng chưa xác định được chi nhánh gần nhất. ");
                if (!branches.isEmpty()) {
                    combo.append(String.format("Bạn có thể thử đặt bàn tại: [Đặt bàn tại %s](/booking?branchId=%s)",
                            branches.get(0).getName(), branches.get(0).getBranchId()));
                }
            } else {
                combo.append("Bạn vui lòng bấm nút 📍 chia sẻ vị trí để tôi tìm chi nhánh gần bạn nhất nhé!");
            }
            return combo.toString();
        }

        // 6.2 Geolocation / Branch Proximity Check (Fix Bug #1: use specific patterns instead of just "gần")
        if (wantsProximity) {
            if (nearestBranch != null && minDistance != Double.MAX_VALUE) {
                return String.format("Dựa trên định vị hiện tại của bạn, chi nhánh gần nhất là **%s** tại địa chỉ **%s** (cách bạn khoảng **%.2f km**). Bạn có muốn tôi hỗ trợ đặt bàn trực tuyến tại đây không? [Đặt bàn tại %s](/booking?branchId=%s)",
                        nearestBranch.getName(), nearestBranch.getAddress(), minDistance, nearestBranch.getName(), nearestBranch.getBranchId());
            } else if (userLat != null && userLng != null) {
                // User shared location but no branch has lat/lng in DB — recalculate just in case
                Branch closest = null;
                double minD = Double.MAX_VALUE;
                for (Branch b : branches) {
                    if (b.getLatitude() != null && b.getLongitude() != null) {
                        double dist = calculateDistance(userLat, userLng, b.getLatitude(), b.getLongitude());
                        if (dist < minD) {
                            minD = dist;
                            closest = b;
                        }
                    }
                }
                if (closest != null) {
                    return String.format("Dựa trên định vị hiện tại của bạn, chi nhánh gần nhất là **%s** tại địa chỉ **%s** (cách bạn khoảng **%.2f km**). Bạn có muốn tôi hỗ trợ đặt bàn tại đây không? [Đặt bàn tại %s](/booking?branchId=%s)",
                            closest.getName(), closest.getAddress(), minD, closest.getName(), closest.getBranchId());
                }
                // Fix Bug #2: always return something when user shared location
                if (!branches.isEmpty()) {
                    Branch firstBranch = branches.get(0);
                    return String.format("Đã nhận diện tọa độ của bạn thành công! Chi nhánh gợi ý dành cho bạn: **%s** tại **%s**. Bạn có thể nhấp vào đây để đặt bàn: [Đặt bàn tại %s](/booking?branchId=%s)",
                            firstBranch.getName(), firstBranch.getAddress(), firstBranch.getName(), firstBranch.getBranchId());
                }
                return "Đã nhận tọa độ của bạn nhưng hiện tại hệ thống chưa có thông tin chi nhánh nào. Vui lòng thử lại sau nhé!";
            }
            // No location shared at all
            return "Tôi chưa có thông tin vị trí của bạn. Bạn vui lòng bấm nút chia sẻ vị trí (nút 📍 ở chân khung chat) và cấp quyền để tôi tìm chi nhánh gần nhất giúp bạn nhé!";
        }

        // 6.3 Context-aware follow-up: "nhà hàng này có cơm tấm không", "chi nhánh này có gì", "món này ở đâu"
        boolean hasContextRef = cleanMsg.contains("này") || cleanMsg.contains("đó") || cleanMsg.contains("vừa") || cleanMsg.contains("vừa rồi");
        boolean asksBranchFood = hasContextRef && (cleanMsg.contains("có món") || cleanMsg.contains("có gì") || cleanMsg.contains("thực đơn") || cleanMsg.contains("menu"));
        boolean asksProductBranch = cleanMsg.contains("món này") || cleanMsg.contains("bán ở đâu")
                || (cleanMsg.contains("chi nhánh nào") && (cleanMsg.contains("món") || cleanMsg.contains("có")));

        // 6.3a "nhà hàng này / chi nhánh này có món X không" — find branch from history, then check product
        if (asksBranchFood) {
            Branch contextBranch = findBranchInHistory(history, branches);
            // Also try to find a product in the current message (e.g. "có món cơm tấm không")
            Product productInMsg = findProductInText(cleanMsg, products);
            if (productInMsg == null) {
                productInMsg = findProductSoft(cleanMsg, products);
            }
            if (contextBranch != null && productInMsg != null) {
                // Check if this product is available at this branch
                List<ProductVariant> pVariants = variantsByProduct.get(productInMsg.getId());
                boolean availableHere = true;
                if (pVariants != null) {
                    boolean hasSpecific = pVariants.stream().anyMatch(pv -> pv.getBranchId() != null);
                    if (hasSpecific) {
                        availableHere = pVariants.stream().anyMatch(pv -> contextBranch.getBranchId().equals(pv.getBranchId()));
                    }
                }
                if (availableHere) {
                    StringBuilder reply = new StringBuilder();
                    reply.append(String.format("Có! Chi nhánh **%s** có phục vụ món **%s**.\n", contextBranch.getName(), productInMsg.getName()));
                    if (pVariants != null && !pVariants.isEmpty()) {
                        reply.append("*Giá bán:* ");
                        List<String> vs = pVariants.stream()
                                .map(pv -> String.format("%,.0f VNĐ (%s)", pv.getPrice(), pv.getName()))
                                .collect(Collectors.toList());
                        reply.append(String.join(", ", vs)).append("\n");
                    }
                    reply.append(String.format("\nBạn có muốn đặt bàn không? [Đặt bàn tại %s](/booking?branchId=%s)", contextBranch.getName(), contextBranch.getBranchId()));
                    return reply.toString();
                } else {
                    return String.format("Rất tiếc, chi nhánh **%s** hiện không phục vụ món **%s**. Bạn có muốn tôi gợi ý chi nhánh khác có món này không?",
                            contextBranch.getName(), productInMsg.getName());
                }
            } else if (contextBranch != null) {
                // User asks about branch menu generically ("nhà hàng này có gì")
                return String.format("Chi nhánh **%s** tại **%s** phục vụ toàn bộ thực đơn của LiteFlow. Bạn muốn tìm hiểu món nào cụ thể? Ví dụ: cơm tấm, phở bò, lẩu thái,...",
                        contextBranch.getName(), contextBranch.getAddress());
            }
        }

        // 6.3b Follow-up about previously discussed product ("món này ở đâu", "chi nhánh nào có món")
        if (asksProductBranch) {
            Product contextProduct = findProductInHistory(history, products);

            if (contextProduct != null) {
                List<ProductVariant> pVariants = variantsByProduct.get(contextProduct.getId());
                List<String> specificBranches = new ArrayList<>();
                boolean allBranches = true;

                if (pVariants != null && !pVariants.isEmpty()) {
                    for (ProductVariant pv : pVariants) {
                        if (pv.getBranchId() != null) {
                            allBranches = false;
                            Branch br = branches.stream().filter(b -> b.getBranchId().equals(pv.getBranchId())).findFirst().orElse(null);
                            if (br != null && !specificBranches.contains(br.getName())) {
                                specificBranches.add(br.getName());
                            }
                        }
                    }
                }

                if (allBranches || specificBranches.isEmpty()) {
                    return String.format("Món **%s** hiện phục vụ tại **tất cả các chi nhánh** thuộc hệ thống LiteFlow. Bạn muốn đặt bàn tại chi nhánh nào? Dưới đây là liên kết đặt bàn trực tuyến:\n\n", contextProduct.getName())
                            + branches.stream().map(b -> String.format("- [Đặt bàn tại %s](/booking?branchId=%s)", b.getName(), b.getBranchId())).collect(Collectors.joining("\n"));
                } else {
                    return String.format("Món **%s** hiện có mặt tại các chi nhánh: **%s**. Bạn có muốn đặt bàn tại chi nhánh nào không?", 
                            contextProduct.getName(), String.join(", ", specificBranches));
                }
            }
        }

        // 6.4 Specific branch distance queries
        if (cleanMsg.contains("cách tôi bao nhiêu") || cleanMsg.contains("cách đây bao nhiêu") || cleanMsg.contains("bao nhiêu km")) {
            for (Branch br : branches) {
                String branchNameNfc = nfc(br.getName().toLowerCase());
                if (cleanMsg.contains(branchNameNfc)) {
                    if (userLat != null && userLng != null && br.getLatitude() != null && br.getLongitude() != null) {
                        double dist = calculateDistance(userLat, userLng, br.getLatitude(), br.getLongitude());
                        return String.format("Chi nhánh **%s** cách vị trí hiện tại của bạn khoảng **%.2f km** nhé.", br.getName(), dist);
                    }
                }
            }
            if (nearestBranch != null) {
                return String.format("Chi nhánh gần nhất là **%s**, cách bạn khoảng **%.2f km**.", nearestBranch.getName(), minDistance);
            }
            return "Tôi chưa có thông tin vị trí của bạn. Bạn vui lòng bật GPS chia sẻ vị trí để tôi tính khoảng cách nhé!";
        }

        // 6.5 Allergen check (Fix Issue #4: use NFC normalization for product names)
        if (cleanMsg.contains("dị ứng") || cleanMsg.contains("kiêng") || cleanMsg.contains("không ăn được") || cleanMsg.contains("tránh")) {
            String allergen = "";
            if (cleanMsg.contains("hành")) {
                allergen = "hành";
            } else if (cleanMsg.contains("tôm")) {
                allergen = "tôm";
            } else if (cleanMsg.contains("bò")) {
                allergen = "bò";
            } else if (cleanMsg.contains("lạc") || cleanMsg.contains("đậu phộng")) {
                allergen = "đậu phộng";
            } else if (cleanMsg.contains("hải sản")) {
                allergen = "hải sản";
            } else if (cleanMsg.contains("sữa")) {
                allergen = "sữa";
            } else if (cleanMsg.contains("trứng")) {
                allergen = "trứng";
            }

            // Try to find product in current message first, then in history
            Product targetProduct = findProductInText(cleanMsg, products);
            if (targetProduct == null) {
                targetProduct = findProductInHistory(history, products);
            }

            if (targetProduct != null && !allergen.isEmpty()) {
                String ingredients = targetProduct.getIngredients();
                boolean isDangerous = false;
                if (ingredients != null) {
                    String cleanIng = nfc(ingredients.toLowerCase());
                    if (cleanIng.contains(allergen)) {
                        isDangerous = true;
                    }
                }
                
                if (isDangerous) {
                    return String.format("Chào bạn! Đối với món **%s**, trong thành phần có chứa **%s** (chi tiết: %s). Vì bạn bị dị ứng với **%s**, bạn **không nên** dùng món này nhé. Bạn có muốn tham khảo các món ăn khác không?", 
                            targetProduct.getName(), allergen, ingredients, allergen);
                } else {
                    return String.format("Chào bạn! Món **%s** có các thành phần: %s. Món này **không chứa** %s trong danh sách thành phần đã ghi nhận nên bạn có thể yên tâm thưởng thức nhé!", 
                            targetProduct.getName(), ingredients != null ? ingredients : "Không ghi rõ", allergen);
                }
            } else if (targetProduct != null) {
                return String.format("Món **%s** có các thành phần sau: %s. Bạn vui lòng kiểm tra xem có thành phần nào mình cần tránh không nhé!", 
                        targetProduct.getName(), targetProduct.getIngredients() != null ? targetProduct.getIngredients() : "Không ghi rõ");
            }
        }

        // 6.6 Booking link queries (Fix Issue #5: dynamic branch matching instead of hardcoded branchId)
        if (cleanMsg.contains("đặt bàn") || cleanMsg.contains("book") || cleanMsg.contains("đăng ký bàn") || cleanMsg.contains("giữ bàn")) {
            // Dynamically match branch name from user message
            Branch targetBranch = null;
            for (Branch b : branches) {
                String branchNameNfc = nfc(b.getName().toLowerCase());
                if (cleanMsg.contains(branchNameNfc)) {
                    targetBranch = b;
                    break;
                }
            }
            // Fallback: try common abbreviations/aliases
            if (targetBranch == null) {
                for (Branch b : branches) {
                    String addr = nfc(b.getAddress() != null ? b.getAddress().toLowerCase() : "");
                    // Check if user mentions street name from address
                    if (cleanMsg.contains("2 tháng 9") || cleanMsg.contains("2/9")) {
                        if (addr.contains("2 tháng 9")) { targetBranch = b; break; }
                    }
                    if (cleanMsg.contains("hải phòng") && addr.contains("hải phòng")) { targetBranch = b; break; }
                    if (cleanMsg.contains("nguyễn hữu thọ") && addr.contains("nguyễn hữu thọ")) { targetBranch = b; break; }
                    if (cleanMsg.contains("lê lợi") && addr.contains("lê lợi")) { targetBranch = b; break; }
                    if (cleanMsg.contains("hùng vương") && addr.contains("hùng vương")) { targetBranch = b; break; }
                }
            }

            if (targetBranch != null) {
                return String.format("Chào bạn! Để đặt bàn tại **%s**, bạn vui lòng nhấp vào liên kết này để mở form và đã được chọn sẵn chi nhánh: [Đặt bàn tại %s](/booking?branchId=%s). Hân hạnh được phục vụ bạn!",
                        targetBranch.getName(), targetBranch.getName(), targetBranch.getBranchId());
            } else if (nearestBranch != null) {
                return String.format("Chào bạn! Dựa trên định vị của bạn, chi nhánh gần nhất là **%s**. Bạn có thể đặt bàn trực tuyến tại đây: [Đặt bàn tại %s](/booking?branchId=%s) (cách bạn khoảng **%.2f km**).",
                        nearestBranch.getName(), nearestBranch.getName(), nearestBranch.getBranchId(), minDistance);
            } else {
                StringBuilder sb = new StringBuilder("Chào bạn! Để đặt bàn trực tuyến tại hệ thống nhà hàng LiteFlow, vui lòng chọn một trong các liên kết điền sẵn chi nhánh dưới đây:\n\n");
                for (Branch b : branches) {
                    sb.append(String.format("- [Đặt bàn tại %s](/booking?branchId=%s)\n", b.getName(), b.getBranchId()));
                }
                return sb.toString();
            }
        }

        // 6.7 Rating Check
        if (cleanMsg.contains("đánh giá cao nhất") || cleanMsg.contains("tốt nhất") || cleanMsg.contains("rating cao nhất") || cleanMsg.contains("ngon nhất")) {
            if (highestRatedBranch != null && maxRating > 0) {
                return String.format("Chào bạn! Chi nhánh được khách hàng đánh giá cao nhất hiện tại là **%s** với điểm trung bình là **%.1f sao** (địa chỉ: %s).",
                        highestRatedBranch.getName(), maxRating, highestRatedBranch.getAddress());
            }
        }

        // 6.8 Table Availability Check
        if (cleanMsg.contains("bàn trống") || cleanMsg.contains("còn bàn")) {
            StringBuilder tableReply = new StringBuilder("Chào bạn! Dưới đây là tình hình bàn trống thực tế tại các chi nhánh:\n\n");
            for (Branch b : branches) {
                List<TableEntity> branchTables = tablesByBranch.get(b.getBranchId());
                long vacant = branchTables != null ? branchTables.stream().filter(t -> "EMPTY".equalsIgnoreCase(t.getStatus())).count() : 0;
                tableReply.append(String.format("- **%s**: Còn **%d** bàn trống.\n", b.getName(), vacant));
                if (branchTables != null && vacant > 0) {
                    tableReply.append("  (Bàn: ");
                    List<String> vacantNames = branchTables.stream()
                            .filter(t -> "EMPTY".equalsIgnoreCase(t.getStatus()))
                            .map(TableEntity::getName)
                            .collect(Collectors.toList());
                    tableReply.append(String.join(", ", vacantNames)).append(")\n");
                }
            }
            tableReply.append("\nBạn có muốn tôi hướng dẫn đặt bàn tại chi nhánh nào không?");
            return tableReply.toString();
        }

        // 6.8b Dedicated Restaurant Rules / Policy Handler (expanded for specific policy topics)
        boolean isPolicyQuery = cleanMsg.contains("quy định") || cleanMsg.contains("nội quy") || cleanMsg.contains("chính sách")
                || cleanMsg.contains("thú cưng") || cleanMsg.contains("chó") || cleanMsg.contains("mèo") || cleanMsg.contains("thú nuôi") || cleanMsg.contains("mang theo")
                || cleanMsg.contains("hút thuốc") || cleanMsg.contains("thuốc lá")
                || cleanMsg.contains("đậu xe") || cleanMsg.contains("đỗ xe") || cleanMsg.contains("gửi xe") || cleanMsg.contains("giữ xe")
                || cleanMsg.contains("trẻ em") || cleanMsg.contains("ghế trẻ em")
                || cleanMsg.contains("sinh nhật") || cleanMsg.contains("ưu đãi")
                || cleanMsg.contains("mang rượu") || cleanMsg.contains("phụ phí");

        if (isPolicyQuery) {
            List<Product> ruleItems = products.stream()
                    .filter(p -> {
                        if (p.getName() == null) return false;
                        String pNameLower = p.getName().toLowerCase();
                        String pDescLower = p.getDescription() != null ? p.getDescription().toLowerCase() : "";
                        String pIngrLower = p.getIngredients() != null ? p.getIngredients().toLowerCase() : "";
                        return pNameLower.contains("quy định") || pNameLower.contains("nội quy") || pNameLower.contains("chính sách")
                                || pDescLower.contains("quy định") || pIngrLower.contains("quy định");
                    })
                    .collect(Collectors.toList());

            if (!ruleItems.isEmpty()) {
                // If user asked about a specific topic (e.g. "thú cưng", "sinh nhật"), filter rules to match that topic first
                List<Product> specificRules = new ArrayList<>();
                for (Product r : ruleItems) {
                    String fullText = (r.getName() + " " + (r.getDescription() != null ? r.getDescription() : "") + " " + (r.getIngredients() != null ? r.getIngredients() : "")).toLowerCase();
                    if (cleanMsg.contains("thú cưng") || cleanMsg.contains("chó") || cleanMsg.contains("mèo") || cleanMsg.contains("thú nuôi")) {
                        if (fullText.contains("thú cưng") || fullText.contains("thú nuôi") || fullText.contains("chó") || fullText.contains("mèo")) specificRules.add(r);
                    } else if (cleanMsg.contains("sinh nhật")) {
                        if (fullText.contains("sinh nhật")) specificRules.add(r);
                    } else if (cleanMsg.contains("hút thuốc") || cleanMsg.contains("thuốc lá")) {
                        if (fullText.contains("hút thuốc") || fullText.contains("thuốc lá")) specificRules.add(r);
                    } else if (cleanMsg.contains("đậu xe") || cleanMsg.contains("đỗ xe") || cleanMsg.contains("gửi xe") || cleanMsg.contains("giữ xe")) {
                        if (fullText.contains("xe") || fullText.contains("đậu") || fullText.contains("đỗ") || fullText.contains("bãi")) specificRules.add(r);
                    }
                }

                List<Product> displayList = !specificRules.isEmpty() ? specificRules : ruleItems;
                StringBuilder rulesReply = new StringBuilder("📋 **Thông tin Quy định & Chính sách của LiteFlow:**\n\n");
                for (Product r : displayList) {
                    rulesReply.append(String.format("🔹 **%s**\n", r.getName()));
                    if (r.getDescription() != null && !r.getDescription().trim().isEmpty()) {
                        rulesReply.append(String.format("   %s\n", r.getDescription()));
                    }
                    if (r.getIngredients() != null && !r.getIngredients().trim().isEmpty()) {
                        rulesReply.append(String.format("   *Nội dung:* %s\n", r.getIngredients()));
                    }
                    rulesReply.append("\n");
                }
                rulesReply.append("Bạn có câu hỏi nào khác về quy định hay đặt bàn không?");
                return rulesReply.toString();
            }
        }

        // 6.9 Menu Search Fallback (improved noise removal + soft matching, filter out 'Quy Định')
        String queryTerm = cleanMsg
                .replace("hệ thống", "")
                .replace("nhà hàng", "")
                .replace("có món", "")
                .replace("bán món", "")
                .replace("tìm món", "")
                .replace("tôi muốn ăn món", "")
                .replace("tôi muốn ăn", " ")
                .replace("có bán", "")
                .replace("không", "")
                .replace("?", "")
                // Fix #3: remove Vietnamese demonstrative pronouns & conversational fillers
                .replace("này", "")
                .replace("đó", "")
                .replace("kia", "")
                .replace("nào", "")
                .replace("vừa", "")
                .replace("mới", "")
                .replace("bạn", "")
                .replace("gợi ý", "")
                .replace("cho tôi", "")
                .replace("ở đây", "")
                .replace("tại đây", "")
                .replaceAll("\\s+", " ")
                .trim();

        List<Product> matched = new ArrayList<>();
        if (!queryTerm.isEmpty()) {
            for (Product p : products) {
                // Filter out non-food rules from standard food search
                String pNameRaw = p.getName() != null ? p.getName().toLowerCase() : "";
                if (pNameRaw.contains("quy định") || pNameRaw.contains("nội quy") || pNameRaw.contains("chính sách")) {
                    continue;
                }
                String name = nfc(pNameRaw);
                String desc = nfc(p.getDescription() != null ? p.getDescription().toLowerCase() : "");
                String ingr = nfc(p.getIngredients() != null ? p.getIngredients().toLowerCase() : "");
                // Fix #2: Exact match OR soft keyword match
                if (cleanMsg.contains(name) || name.contains(queryTerm) || desc.contains(queryTerm) || ingr.contains(queryTerm)
                        || softMatchProduct(queryTerm, name)) {
                    matched.add(p);
                }
            }
        }

        if (!matched.isEmpty()) {
            StringBuilder menuReply = new StringBuilder("LiteFlow hiện có món ăn bạn đang tìm kiếm:\n\n");
            for (Product p : matched) {
                menuReply.append(String.format("🍔 **%s**\n", p.getName()));
                if (p.getDescription() != null) {
                    menuReply.append(String.format("   *Mô tả:* %s\n", p.getDescription()));
                }
                if (p.getIngredients() != null) {
                    menuReply.append(String.format("   *Thành phần:* %s\n", p.getIngredients()));
                }
                List<ProductVariant> pVariants = variantsByProduct.get(p.getId());
                if (pVariants != null && !pVariants.isEmpty()) {
                    menuReply.append("   *Giá bán:* ");
                    List<String> varStrings = new ArrayList<>();
                    for (ProductVariant pv : pVariants) {
                        String costStr = String.format("%,.0f VNĐ", pv.getPrice());
                        if (pv.getBranchId() != null) {
                            varStrings.add(String.format("%s (%s - Chi nhánh mã %s)", costStr, pv.getName(), pv.getBranchId()));
                        } else {
                            varStrings.add(String.format("%s (%s)", costStr, pv.getName()));
                        }
                    }
                    menuReply.append(String.join(", ", varStrings)).append("\n");
                }
                menuReply.append("\n");
            }
            menuReply.append("Bạn có muốn đặt bàn ghé chi nhánh gần nhất để thưởng thức không?");
            return menuReply.toString();
        }

        // 6.10 If context history exists, provide a smart clarification response instead of repeating full initial welcome prompt
        if (history != null && !history.isEmpty()) {
            return "Tôi chưa hiểu rõ thông tin bạn cần tìm. Bạn vui lòng cho tôi biết cụ thể tên món ăn, chi nhánh hoặc chủ đề bạn muốn hỗ trợ (ví dụ: bàn trống, đặt bàn, định vị) nhé!";
        }

        // 6.11 General greeting fallback (first message or unrecognized intent without history)
        StringBuilder generalResponse = new StringBuilder("Chào bạn! Tôi là Trợ lý ảo AI của hệ thống nhà hàng LiteFlow. Tôi có thể hỗ trợ bạn tìm kiếm món ăn, kiểm tra bàn trống, tính khoảng cách hay gợi ý nhà hàng gần nhất.\n\nHiện tại hệ thống đang hoạt động các chi nhánh:\n");
        for (Branch b : branches) {
            generalResponse.append(String.format("- **%s**: %s (SĐT: %s)\n", b.getName(), b.getAddress(), b.getPhone()));
        }
        generalResponse.append("\nHãy cho tôi biết bạn muốn tìm hiểu thông tin gì nhé!");
        return generalResponse.toString();
    }

    // --- Helper: Find a product mentioned in a text string using NFC-normalized EXACT comparison ---
    private Product findProductInText(String normalizedText, List<Product> products) {
        for (Product p : products) {
            String pName = nfc(p.getName().toLowerCase());
            if (normalizedText.contains(pName)) {
                return p;
            }
        }
        return null;
    }

    // --- Helper: Find a product by SOFT keyword matching (Fix #2) ---
    // Splits product name into tokens; if user text contains 2+ tokens (or 1 for short names), it's a match.
    private Product findProductSoft(String normalizedText, List<Product> products) {
        Product bestMatch = null;
        int bestScore = 0;
        for (Product p : products) {
            String pName = nfc(p.getName().toLowerCase());
            String[] tokens = pName.split("\\s+");
            int matchCount = 0;
            for (String token : tokens) {
                if (token.length() >= 2 && normalizedText.contains(token)) {
                    matchCount++;
                }
            }
            int threshold = tokens.length <= 2 ? 1 : 2;
            if (matchCount >= threshold && matchCount > bestScore) {
                bestScore = matchCount;
                bestMatch = p;
            }
        }
        return bestMatch;
    }

    // --- Helper: Soft match for menu search — checks if queryTerm shares keywords with product name ---
    private boolean softMatchProduct(String queryTerm, String productName) {
        String[] queryTokens = queryTerm.split("\\s+");
        String[] nameTokens = productName.split("\\s+");
        int matchCount = 0;
        for (String qt : queryTokens) {
            if (qt.length() < 2) continue;
            for (String nt : nameTokens) {
                if (nt.contains(qt) || qt.contains(nt)) {
                    matchCount++;
                    break;
                }
            }
        }
        return matchCount >= 1 && ((double) matchCount / nameTokens.length) >= 0.3;
    }

    // --- Helper: Find a product mentioned in conversation history (scan from most recent) ---
    private Product findProductInHistory(List<HistoryMessage> history, List<Product> products) {
        if (history == null || history.isEmpty()) return null;
        for (int i = history.size() - 1; i >= 0; i--) {
            HistoryMessage h = history.get(i);
            if (h.getText() == null) continue;
            String hText = nfc(h.getText().toLowerCase());
            Product found = findProductInText(hText, products);
            if (found != null) return found;
            // Also try soft match
            Product soft = findProductSoft(hText, products);
            if (soft != null) return soft;
        }
        return null;
    }

    // --- Helper: Find a branch mentioned in conversation history (scan from most recent) ---
    private Branch findBranchInHistory(List<HistoryMessage> history, List<Branch> branches) {
        if (history == null || history.isEmpty()) return null;
        for (int i = history.size() - 1; i >= 0; i--) {
            HistoryMessage h = history.get(i);
            if (h.getText() == null) continue;
            String hText = nfc(h.getText().toLowerCase());
            for (Branch b : branches) {
                String bName = nfc(b.getName().toLowerCase());
                if (hText.contains(bName)) {
                    return b;
                }
                // Also check address
                if (b.getAddress() != null) {
                    String bAddr = nfc(b.getAddress().toLowerCase());
                    if (hText.contains(bAddr)) {
                        return b;
                    }
                }
            }
        }
        return null;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
