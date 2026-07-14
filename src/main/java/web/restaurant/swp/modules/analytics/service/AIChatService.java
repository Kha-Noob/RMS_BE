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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
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

    @Value("${openai.api.key:}")
    private String apiKey;

    public String getChatResponse(String message, Double userLat, Double userLng) {
        // 1. Fetch active branches
        List<Branch> branches = branchRepository.findAllByIsActiveTrue();

        // 2. Fetch all reviews and calculate average ratings
        List<CustomerReview> reviews = customerReviewRepository.findAll();
        Map<String, List<CustomerReview>> reviewsByBranch = reviews.stream()
                .collect(Collectors.groupingBy(CustomerReview::getBranchId));

        // 3. Fetch menu products and variants
        List<Product> products = productRepository.findByIsActiveTrue();
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

                log.info("Built System Prompt for Gemini: {}", systemPrompt);

                String requestBody = "{"
                        + "\"contents\": [{"
                        + "\"parts\": [{"
                        + "\"text\": \"" + (systemPrompt + "Khách hàng hỏi: " + message).replace("\n", "\\n").replace("\"", "\\\"") + "\""
                        + "}]"
                        + "}]"
                        + "}";

                HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(15))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode root = new ObjectMapper().readTree(response.body());
                    return root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
                } else {
                    log.warn("Gemini API returned status code: {}", response.statusCode());
                }
            } catch (Exception e) {
                log.error("Failed to generate AI response, falling back to rule-based response", e);
            }
        }

        // 6. Fallback Rule-Based Engine
        String cleanMsg = message.toLowerCase();
        
        // Geolocation Check
        if (cleanMsg.contains("gần nhất") || cleanMsg.contains("gần đây") || cleanMsg.contains("định vị") || cleanMsg.contains("location")) {
            if (nearestBranch != null) {
                return String.format("Dựa trên định vị hiện tại của bạn, chi nhánh gần nhất là **%s** tại địa chỉ **%s** (cách bạn khoảng **%.2f km**). Bạn có muốn tôi hỗ trợ đặt bàn trực tuyến tại đây không? [Đặt bàn tại %s](/booking?branchId=%s)",
                        nearestBranch.getName(), nearestBranch.getAddress(), minDistance, nearestBranch.getName(), nearestBranch.getBranchId());
            } else {
                return "Tôi không thể định vị được bạn. Bạn vui lòng bấm nút chia sẻ vị trí (nút 📍 ở chân khung chat) và cấp quyền để tôi tìm chi nhánh gần nhất giúp bạn nhé!";
            }
        }

        // Specific branch distance queries
        if (cleanMsg.contains("cách tôi bao nhiêu") || cleanMsg.contains("cách đây bao nhiêu") || cleanMsg.contains("bao nhiêu km")) {
            if (cleanMsg.contains("2 tháng 9") || cleanMsg.contains("2/9")) {
                Branch target = branches.stream().filter(br -> br.getBranchId().equals("01-2thang9")).findFirst().orElse(null);
                if (target != null && userLat != null && userLng != null) {
                    double dist = calculateDistance(userLat, userLng, target.getLatitude(), target.getLongitude());
                    return String.format("Chi nhánh **%s** cách vị trí hiện tại của bạn khoảng **%.2f km** nhé.", target.getName(), dist);
                }
            }
            for (Branch br : branches) {
                if (cleanMsg.contains(br.getName().toLowerCase())) {
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

        // Specific booking link queries (placed first to avoid being intercepted by table check)
        if (cleanMsg.contains("đặt bàn") || cleanMsg.contains("book") || cleanMsg.contains("đăng ký bàn") || cleanMsg.contains("giữ bàn")) {
            Branch targetBranch = null;
            if (cleanMsg.contains("2 tháng 9") || cleanMsg.contains("2/9")) {
                targetBranch = branches.stream().filter(b -> b.getBranchId().equals("01-2thang9")).findFirst().orElse(null);
            } else if (cleanMsg.contains("nguyễn hữu thọ") || cleanMsg.contains("nguyên hữu thọ")) {
                targetBranch = branches.stream().filter(b -> b.getBranchId().equals("11-NguyenHuuTho")).findFirst().orElse(null);
            } else if (cleanMsg.contains("hải phòng")) {
                targetBranch = branches.stream().filter(b -> b.getBranchId().equals("21-HaiPhong")).findFirst().orElse(null);
            } else if (cleanMsg.contains("hợp tác 2")) {
                targetBranch = branches.stream().filter(b -> b.getBranchId().equals("02-external")).findFirst().orElse(null);
            } else if (cleanMsg.contains("hợp tác 3")) {
                targetBranch = branches.stream().filter(b -> b.getBranchId().equals("03-sushi")).findFirst().orElse(null);
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

        // Rating Check
        if (cleanMsg.contains("đánh giá cao nhất") || cleanMsg.contains("tốt nhất") || cleanMsg.contains("rating cao nhất") || cleanMsg.contains("ngon nhất")) {
            if (highestRatedBranch != null && maxRating > 0) {
                return String.format("Chào bạn! Chi nhánh được khách hàng đánh giá cao nhất hiện tại là **%s** với điểm trung bình là **%.1f sao** (địa chỉ: %s).",
                        highestRatedBranch.getName(), maxRating, highestRatedBranch.getAddress());
            }
        }

        // Table Availability Check
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

        // Menu Search Fallback (Cleaned match logic)
        // Extract query term by removing conversational noise
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
                .trim();

        List<Product> matched = new ArrayList<>();
        if (!queryTerm.isEmpty()) {
            for (Product p : products) {
                String name = p.getName().toLowerCase();
                String desc = p.getDescription() != null ? p.getDescription().toLowerCase() : "";
                String ingr = p.getIngredients() != null ? p.getIngredients().toLowerCase() : "";
                if (cleanMsg.contains(name) || name.contains(queryTerm) || desc.contains(queryTerm) || ingr.contains(queryTerm)) {
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

        // General greeting fallback
        StringBuilder generalResponse = new StringBuilder("Chào bạn! Tôi là Trợ lý ảo AI của hệ thống nhà hàng LiteFlow. Tôi có thể hỗ trợ bạn tìm kiếm món ăn, kiểm tra bàn trống, tính khoảng cách hay gợi ý nhà hàng gần nhất.\n\nHiện tại hệ thống đang hoạt động các chi nhánh:\n");
        for (Branch b : branches) {
            generalResponse.append(String.format("- **%s**: %s (SĐT: %s)\n", b.getName(), b.getAddress(), b.getPhone()));
        }
        generalResponse.append("\nHãy cho tôi biết bạn muốn tìm hiểu thông tin gì nhé!");
        return generalResponse.toString();
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
