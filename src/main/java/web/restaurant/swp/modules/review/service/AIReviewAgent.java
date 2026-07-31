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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
        log.info("AI Review Agent processing review {} from source {} with rating {}", 
                review.getId(), review.getSource(), review.getRating());

        // 1. Initial defaults
        String parsedSentiment = review.getRating() > 3 ? "POSITIVE" : (review.getRating() == 3 ? "NEUTRAL" : "NEGATIVE");
        String apologyVi = "Cảm ơn quý khách " + review.getCustomerName() + " đã đánh giá. Rất mong được phục vụ quý khách lần sau!";
        String apologyEn = "Thank you " + review.getCustomerName() + " for your review. We look forward to serving you again!";
        String vType = "None";
        double vValue = 0.0;

        // Fetch Order details for context if available
        double orderTotal = 0.0;
        String orderItemsText = "";
        if (review.getOrderId() != null) {
            Optional<Order> orderOpt = orderRepository.findById(review.getOrderId());
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                orderTotal = order.getTotalAmount();
                List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
                orderItemsText = details.stream()
                        .map(d -> d.getVariant().getProduct().getName() + " (x" + d.getQuantity() + ")")
                        .collect(Collectors.joining(", "));
            }
        }

        String prompt = "Bạn là Trợ lý AI Chăm sóc Khách hàng của nhà hàng.\n"
                + "Hãy phân tích đánh giá sau và đề xuất phản hồi:\n"
                + "Khách hàng: \"" + review.getCustomerName() + "\"\n"
                + "Đánh giá: " + review.getRating() + " sao\n"
                + "Bình luận: \"" + (review.getComment() != null ? review.getComment() : "Không viết bình luận") + "\"\n"
                + "Đơn hàng liên quan (ID: " + (review.getOrderId() != null ? review.getOrderId() : "N/A") + "): Tổng tiền " + orderTotal + " VNĐ. Các món đã gọi: [" + orderItemsText + "].\n\n"
                + "Hãy thực hiện:\n"
                + "1. Phân tích cảm xúc của bình luận (sentiment): POSITIVE, NEGATIVE, hoặc NEUTRAL.\n"
                + "2. Viết câu phản hồi lịch sự, tự nhiên bằng Tiếng Việt (apologyResponseVi).\n"
                + "3. Viết câu phản hồi tương đương bằng Tiếng Anh (apologyResponseEn).\n"
                + "4. Chỉ dành cho cảm xúc NEGATIVE (phàn nàn nặng), hãy đề xuất đền bù thích hợp:\n"
                + "   - Loại voucher (voucherType): \"PercentDiscount\", \"FlatDiscount\", hoặc \"None\" (nếu không cần đền bù)\n"
                + "   - Giá trị voucher (voucherValue): số tiền đền bù (ví dụ: 20000, 50000) hoặc % chiết khấu (ví dụ: 5, 10) (0 nếu None)\n"
                + "   - Lý do đề xuất (reasonForValue).\n\n"
                + "Hãy trả về kết quả định dạng JSON thô với cấu trúc chính xác sau (không kèm mã markdown hay ký tự khác, chỉ trả về chuỗi JSON thô hợp lệ):\n"
                + "{\n"
                + "  \"sentiment\": \"POSITIVE\" hoặc \"NEGATIVE\" hoặc \"NEUTRAL\",\n"
                + "  \"apologyResponseVi\": \"Lời phản hồi tiếng Việt...\",\n"
                + "  \"apologyResponseEn\": \"Lời phản hồi tiếng Anh...\",\n"
                + "  \"voucherType\": \"PercentDiscount\" hoặc \"FlatDiscount\" hoặc \"None\",\n"
                + "  \"voucherValue\": giá trị số,\n"
                + "  \"reasonForValue\": \"Lý do...\"\n"
                + "}\n";

        if (apiKey != null && !apiKey.trim().isEmpty()) {
            try {
                String requestBody = "{"
                        + "\"contents\": [{"
                        + "\"parts\": [{"
                        + "\"text\": \"" + prompt.replace("\n", "\\n").replace("\"", "\\\"") + "\""
                        + "}]"
                        + "}]"
                        + "}";

                HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(java.time.Duration.ofSeconds(15))
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey))
                        .timeout(java.time.Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String body = response.body();
                    try {
                        JsonNode root = new ObjectMapper().readTree(body);
                        String rawText = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
                        String jsonResponse = rawText;
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

                        jsonResponse = jsonResponse.trim();
                        parsedSentiment = extractJsonField(jsonResponse, "sentiment", parsedSentiment).toUpperCase();
                        apologyVi = extractJsonField(jsonResponse, "apologyResponseVi", apologyVi);
                        apologyEn = extractJsonField(jsonResponse, "apologyResponseEn", apologyEn);
                        vType = extractJsonField(jsonResponse, "voucherType", vType);
                        String vValStr = extractJsonField(jsonResponse, "voucherValue", "0.0");
                        try {
                            vValue = Double.parseDouble(vValStr.replaceAll("[^0-9.]", ""));
                        } catch (Exception ignored) {}
                    } catch (Exception parseEx) {
                        log.error("Failed to parse Gemini response via Jackson", parseEx);
                    }
                }
            } catch (Exception e) {
                log.error("Error calling Gemini API for omnichannel review resolution", e);
            }
        } else {
            // Default rule-based compensation for Negative reviews without API Key
            if ("NEGATIVE".equals(parsedSentiment)) {
                apologyVi = "Chúng tôi vô cùng xin lỗi vì trải nghiệm không tốt của quý khách tại nhà hàng. Ban quản lý đã ghi nhận phản hồi và sẽ khắc phục ngay lập tức.";
                apologyEn = "We are deeply sorry for your unsatisfactory experience at our restaurant. Management has noted your feedback and will rectify it immediately.";
                vType = "PercentDiscount";
                vValue = 10.0;
            }
        }

        // Save sentiment and suggested responses in review object
        review.setSentiment(parsedSentiment);
        review.setResponseVi(apologyVi);
        review.setResponseEn(apologyEn);
        
        // Auto-Generate Promotion Voucher if compensation proposed
        String generatedCode = null;
        if (!"None".equalsIgnoreCase(vType) && vValue > 0) {
            Tenant tenant = null;
            Optional<Branch> branchOpt = branchRepository.findById(review.getBranchId());
            if (branchOpt.isPresent()) {
                tenant = branchOpt.get().getTenant();
            }

            String codeSuffix = String.format("%04d", new Random().nextInt(10000));
            generatedCode = "补偿_" + codeSuffix; // "DENBU_" in Vietnamese but using alphanumeric or standard prefix
            generatedCode = "DENBU_" + codeSuffix;

            Promotion promo = Promotion.builder()
                    .name("Đền bù đánh giá: " + review.getCustomerName())
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
            log.info("AI Review Agent generated compensation voucher {} for review {}", generatedCode, review.getId());
        }

        // Save final review state
        CustomerReview savedReview = customerReviewRepository.save(review);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("reviewId", savedReview.getId());
        res.put("rating", savedReview.getRating());
        res.put("source", savedReview.getSource());
        res.put("sentiment", savedReview.getSentiment());
        res.put("responseVi", savedReview.getResponseVi());
        res.put("responseEn", savedReview.getResponseEn());
        res.put("isApproved", savedReview.getIsApproved());
        if (generatedCode != null) {
            res.put("voucherGenerated", true);
            res.put("voucherCode", generatedCode);
            res.put("voucherType", vType);
            res.put("voucherValue", vValue);
        } else {
            res.put("voucherGenerated", false);
        }
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
