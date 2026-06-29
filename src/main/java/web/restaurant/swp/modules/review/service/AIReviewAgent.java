package web.restaurant.swp.modules.review.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.restaurant.swp.modules.review.model.CustomerReview;
import web.restaurant.swp.modules.review.repository.CustomerReviewRepository;
import web.restaurant.swp.modules.pos.model.Order;
import web.restaurant.swp.modules.pos.model.OrderDetail;
import web.restaurant.swp.modules.pos.repository.OrderRepository;
import web.restaurant.swp.modules.pos.repository.OrderDetailRepository;
import web.restaurant.swp.modules.promotion.model.Promotion;
import web.restaurant.swp.modules.promotion.repository.PromotionRepository;
import web.restaurant.swp.modules.branch.model.Branch;
import web.restaurant.swp.modules.branch.repository.BranchRepository;
import web.restaurant.swp.modules.tenant.model.Tenant;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIReviewAgent {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final PromotionRepository promotionRepository;
    private final BranchRepository branchRepository;
    private final CustomerReviewRepository customerReviewRepository;

    @Value("${openai.api.key:}")
    private String apiKey;

    @Transactional
    public Map<String, Object> processReviewAndGenerateResolution(CustomerReview review) {
        log.info("AI Review Agent processing review {} with rating {}", review.getId(), review.getRating());
        
        // Save review first
        CustomerReview savedReview = customerReviewRepository.save(review);

        // Standard positive review response
        if (savedReview.getRating() > 2) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("reviewId", savedReview.getId());
            res.put("rating", savedReview.getRating());
            res.put("response", "Cảm ơn quý khách " + savedReview.getCustomerName() + " đã dành thời gian đánh giá và chia sẻ trải nghiệm tuyệt vời tại nhà hàng. Rất mong được phục vụ quý khách trong những lần tiếp theo!");
            res.put("voucherGenerated", false);
            return res;
        }

        // Fetch Order details for context
        double orderTotal = 0.0;
        String orderItemsText = "";
        Order order = null;
        if (savedReview.getOrderId() != null) {
            Optional<Order> orderOpt = orderRepository.findById(savedReview.getOrderId());
            if (orderOpt.isPresent()) {
                order = orderOpt.get();
                orderTotal = order.getTotalAmount();
                List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
                orderItemsText = details.stream()
                        .map(d -> d.getVariant().getProduct().getName() + " (x" + d.getQuantity() + ")")
                        .collect(Collectors.joining(", "));
            }
        }

        String prompt = "Bạn là Trợ lý AI Chăm sóc Khách hàng & Giải quyết Khiếu nại của nhà hàng. "
                + "Khách hàng \"" + savedReview.getCustomerName() + "\" đánh giá " + savedReview.getRating() + " sao với ý kiến: \"" + (savedReview.getComment() != null ? savedReview.getComment() : "Không có bình luận") + "\".\n"
                + "Đơn hàng liên quan (ID: " + (savedReview.getOrderId() != null ? savedReview.getOrderId() : "N/A") + "): Tổng tiền " + orderTotal + " VNĐ. Các món đã gọi: [" + orderItemsText + "].\n"
                + "Hãy phân tích và viết một câu xin lỗi vô cùng chân thành, lịch sự và đồng cảm bằng tiếng Việt. "
                + "Đồng thời đề xuất loại đền bù thích hợp:\n"
                + "- Nếu phàn nàn nhẹ: FlatDiscount 20.000đ hoặc PercentDiscount 5%.\n"
                + "- Nếu phàn nàn trung bình/nặng: FlatDiscount 50.000đ - 100.000đ hoặc PercentDiscount 10% - 15%.\n"
                + "Hãy trả về kết quả định dạng JSON với cấu trúc chính xác như sau (không kèm mã markdown hay từ khóa khác, chỉ trả về chuỗi JSON thô hợp lệ):\n"
                + "{\n"
                + "  \"apologyResponse\": \"Lời xin lỗi chi tiết...\",\n"
                + "  \"voucherType\": \"PercentDiscount\" hoặc \"FlatDiscount\",\n"
                + "  \"voucherValue\": giá trị số,\n"
                + "  \"reasonForValue\": \"Lý do đền bù...\"\n"
                + "}\n";

        String apology = "Rất xin lỗi quý khách vì trải nghiệm không tốt tại nhà hàng. Chúng tôi ghi nhận phản hồi và sẽ khắc phục ngay lập tức.";
        String vType = "PercentDiscount";
        double vValue = 10.0;

        if (apiKey != null && !apiKey.trim().isEmpty()) {
            try {
                String requestBody = "{"
                        + "\"contents\": [{"
                        + "\"parts\": [{"
                        + "\"text\": \"" + prompt.replace("\n", "\\n").replace("\"", "\\\"") + "\""
                        + "}]"
                        + "}]"
                        + "}";

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String body = response.body();
                    int textStart = body.indexOf("\"text\": \"") + 9;
                    int textEnd = body.indexOf("\"", textStart);
                    if (textStart > 8 && textEnd > textStart) {
                        String jsonResponse = body.substring(textStart, textEnd).replace("\\n", "\n").replace("\\\"", "\"");
                        if (jsonResponse.contains("```json")) {
                            jsonResponse = jsonResponse.substring(jsonResponse.indexOf("```json") + 7);
                            if (jsonResponse.contains("```")) {
                                jsonResponse = jsonResponse.substring(0, jsonResponse.indexOf("```"));
                            }
                        } else if (jsonResponse.contains("```")) {
                            jsonResponse = jsonResponse.substring(jsonResponse.indexOf("```") + 3);
                            if (jsonResponse.contains("```")) {
                                jsonResponse = jsonResponse.substring(0, jsonResponse.indexOf("```"));
                            }
                        }
                        
                        // Parse simple JSON fields manually or via string extraction to keep it lightweight
                        apology = extractJsonField(jsonResponse, "apologyResponse", apology);
                        vType = extractJsonField(jsonResponse, "voucherType", vType);
                        String vValStr = extractJsonField(jsonResponse, "voucherValue", "10.0");
                        try {
                            vValue = Double.parseDouble(vValStr.replaceAll("[^0-9.]", ""));
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                log.error("Error calling Gemini API for review resolution", e);
            }
        }

        // Auto-Generate Promotion Voucher
        Tenant tenant = null;
        Optional<Branch> branchOpt = branchRepository.findById(savedReview.getBranchId());
        if (branchOpt.isPresent()) {
            tenant = branchOpt.get().getTenant();
        }

        String codeSuffix = String.format("%04d", new Random().nextInt(10000));
        String generatedCode = "SORRY_" + codeSuffix;

        Promotion promo = Promotion.builder()
                .name("Đền bù trải nghiệm khách hàng: " + savedReview.getCustomerName())
                .promoCode(generatedCode)
                .type(vType)
                .discountValue(vValue)
                .minOrderValue(0.0)
                .maxUsageCount(1)
                .currentUsageCount(0)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(30))
                .isActive(true)
                .tenant(tenant)
                .build();

        promotionRepository.save(promo);
        log.info("Autonomous Review Agent created compensation voucher {} for tenant {}", 
                generatedCode, tenant != null ? tenant.getTenantId() : "N/A");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("reviewId", savedReview.getId());
        res.put("rating", savedReview.getRating());
        res.put("response", apology);
        res.put("voucherGenerated", true);
        res.put("voucherCode", generatedCode);
        res.put("voucherType", vType);
        res.put("voucherValue", vValue);
        
        return res;
    }

    private String extractJsonField(String json, String field, String defaultValue) {
        String pattern = "\"" + field + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) return defaultValue;
        int start = idx + pattern.length();
        // find first quote or number
        while (start < json.length() && (Character.isWhitespace(json.charAt(start)) || json.charAt(start) == ':')) {
            start++;
        }
        if (start >= json.length()) return defaultValue;
        if (json.charAt(start) == '"') {
            // String value
            start++;
            int end = json.indexOf("\"", start);
            if (end != -1) {
                return json.substring(start, end);
            }
        } else {
            // Number value or Boolean value
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-' || Character.isLetter(json.charAt(end)))) {
                end++;
            }
            return json.substring(start, end);
        }
        return defaultValue;
    }
}
