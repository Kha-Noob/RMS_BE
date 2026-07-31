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

        // Build contextual guidance based on rating
        String ratingGuidance;
        if (review.getRating() <= 2) {
            ratingGuidance = "Đây là đánh giá RẤT TỆ (1-2 sao). Khách hàng có thể rất thất vọng. "
                    + "Hãy sinh 3 câu trả lời KHÁC NHAU về phong cách:\n"
                    + "  - Câu 1: Xin lỗi chân thành, hứa khắc phục và mời quay lại.\n"
                    + "  - Câu 2: Lắng nghe và hỏi thêm phần chưa vừa ý để cải thiện.\n"
                    + "  - Câu 3: Xin lỗi và đề nghị liên hệ trực tiếp để giải quyết vấn đề.";
        } else if (review.getRating() == 3) {
            ratingGuidance = "Đây là đánh giá TRUNG BÌNH (3 sao). Khách hàng có thể hài lòng một phần. "
                    + "Hãy sinh 3 câu trả lời KHÁC NHAU về phong cách:\n"
                    + "  - Câu 1: Cảm ơn, ghi nhận và hứa cải thiện.\n"
                    + "  - Câu 2: Hỏi về phần nào chưa đáp ứng mong đợi để khắc phục.\n"
                    + "  - Câu 3: Cảm ơn và mời trải nghiệm lại để thấy sự thay đổi.";
        } else if (review.getRating() == 4) {
            ratingGuidance = "Đây là đánh giá TỐT (4 sao). Khách hàng khá hài lòng. "
                    + "Hãy sinh 3 câu trả lời KHÁC NHAU về phong cách:\n"
                    + "  - Câu 1: Cảm ơn chân thành và vui mừng khách hài lòng.\n"
                    + "  - Câu 2: Cảm ơn và hỏi nhẹ nhàng về điểm nào chưa đạt 5 sao.\n"
                    + "  - Câu 3: Cảm ơn, tự hào về đội ngũ và mời quay lại.";
        } else {
            ratingGuidance = "Đây là đánh giá XUẤT SẮC (5 sao). Khách hàng rất hài lòng. "
                    + "Hãy sinh 3 câu trả lời KHÁC NHAU về phong cách:\n"
                    + "  - Câu 1: Cảm ơn nhiệt tình, tự hào về chất lượng.\n"
                    + "  - Câu 2: Cảm ơn và chia sẻ niềm vui, mời chia sẻ trải nghiệm với bạn bè.\n"
                    + "  - Câu 3: Cảm ơn, khẳng định cam kết duy trì chất lượng tốt nhất.";
        }

        String prompt = "Bạn là Trợ lý AI Chăm sóc Khách hàng của nhà hàng.\n"
                + "Hãy phân tích đánh giá sau và đề xuất phản hồi:\n"
                + "Khách hàng: \"" + review.getCustomerName() + "\"\n"
                + "Đánh giá: " + review.getRating() + " sao\n"
                + "Bình luận: \"" + (review.getComment() != null ? review.getComment() : "Không viết bình luận") + "\"\n"
                + "Đơn hàng liên quan (ID: " + (review.getOrderId() != null ? review.getOrderId() : "N/A") + "): Tổng tiền " + orderTotal + " VNĐ. Các món đã gọi: [" + orderItemsText + "].\n\n"
                + ratingGuidance + "\n\n"
                + "Hãy thực hiện:\n"
                + "1. Phân tích cảm xúc của bình luận (sentiment): POSITIVE, NEGATIVE, hoặc NEUTRAL.\n"
                + "2. Viết 3 câu phản hồi tiếng Việt KHÁC NHAU về giọng điệu và cách tiếp cận (suggestion1Vi, suggestion2Vi, suggestion3Vi), mỗi câu 1-3 câu, tự nhiên, phù hợp tone nhà hàng.\n"
                + "3. Viết 3 câu phản hồi tiếng Anh tương đương (suggestion1En, suggestion2En, suggestion3En).\n"
                + "4. Chỉ dành cho cảm xúc NEGATIVE, đề xuất đền bù thích hợp:\n"
                + "   - Loại voucher (voucherType): \"PercentDiscount\", \"FlatDiscount\", hoặc \"None\"\n"
                + "   - Giá trị voucher (voucherValue): số tiền hoặc % (0 nếu None)\n"
                + "   - Lý do đề xuất (reasonForValue).\n\n"
                + "Hãy trả về kết quả định dạng JSON thô với cấu trúc chính xác sau (không kèm mã markdown hay ký tự khác, chỉ trả về chuỗi JSON thô hợp lệ):\n"
                + "{\n"
                + "  \"sentiment\": \"POSITIVE\" hoặc \"NEGATIVE\" hoặc \"NEUTRAL\",\n"
                + "  \"suggestion1Vi\": \"Câu trả lời tiếng Việt gợi ý 1...\",\n"
                + "  \"suggestion2Vi\": \"Câu trả lời tiếng Việt gợi ý 2...\",\n"
                + "  \"suggestion3Vi\": \"Câu trả lời tiếng Việt gợi ý 3...\",\n"
                + "  \"suggestion1En\": \"Reply suggestion 1 in English...\",\n"
                + "  \"suggestion2En\": \"Reply suggestion 2 in English...\",\n"
                + "  \"suggestion3En\": \"Reply suggestion 3 in English...\",\n"
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

                        // Parse 3 suggestions
                        String s1Vi = extractJsonField(jsonResponse, "suggestion1Vi", "");
                        String s2Vi = extractJsonField(jsonResponse, "suggestion2Vi", "");
                        String s3Vi = extractJsonField(jsonResponse, "suggestion3Vi", "");
                        String s1En = extractJsonField(jsonResponse, "suggestion1En", "");
                        String s2En = extractJsonField(jsonResponse, "suggestion2En", "");
                        String s3En = extractJsonField(jsonResponse, "suggestion3En", "");

                        // Combine with ||| separator (non-empty only)
                        java.util.List<String> viParts = new java.util.ArrayList<>();
                        java.util.List<String> enParts = new java.util.ArrayList<>();
                        if (!s1Vi.isEmpty()) viParts.add(s1Vi);
                        if (!s2Vi.isEmpty()) viParts.add(s2Vi);
                        if (!s3Vi.isEmpty()) viParts.add(s3Vi);
                        if (!s1En.isEmpty()) enParts.add(s1En);
                        if (!s2En.isEmpty()) enParts.add(s2En);
                        if (!s3En.isEmpty()) enParts.add(s3En);

                        if (!viParts.isEmpty()) apologyVi = String.join("|||", viParts);
                        if (!enParts.isEmpty()) apologyEn = String.join("|||", enParts);

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
            // Default rule-based suggestions without API Key (3 suggestions combined with |||)
            if (review.getRating() <= 2) {
                apologyVi = "Chúng tôi vô cùng xin lỗi vì trải nghiệm chưa tốt của quý khách. Chúng tôi sẽ cố gắng cải thiện ngay."
                        + "|||" + "Cảm ơn quý khách đã phản hồi. Quý khách có thể cho chúng tôi biết thêm về điều chưa vừa ý để chúng tôi khắc phục không ạ?"
                        + "|||" + "Xin lỗi quý khách vì những bất tiện đã gặp phải. Vui lòng liên hệ trực tiếp với chúng tôi để được hỗ trợ giải quyết.";
                apologyEn = "We sincerely apologize for your unsatisfactory experience. We will work to improve immediately."
                        + "|||" + "Thank you for your feedback. Could you share more details about what didn't meet your expectations so we can improve?"
                        + "|||" + "We're sorry for the inconvenience. Please contact us directly so we can resolve this for you.";
                vType = "PercentDiscount";
                vValue = 10.0;
            } else if (review.getRating() == 3) {
                apologyVi = "Cảm ơn quý khách đã đánh giá. Chúng tôi sẽ ghi nhận và tiếp tục cải thiện chất lượng."
                        + "|||" + "Cảm ơn phản hồi của quý khách! Quý khách có thể chia sẻ điều gì chưa đáp ứng mong đợi để chúng tôi cải thiện không ạ?"
                        + "|||" + "Cảm ơn quý khách! Chúng tôi mong được phục vụ quý khách lần sau tốt hơn và tạo ấn tượng xứng đáng.";
                apologyEn = "Thank you for your review. We will take note and continue improving our quality."
                        + "|||" + "Thank you for your feedback! Could you share what didn't meet your expectations so we can improve?"
                        + "|||" + "Thank you! We hope to serve you better next time and create a truly impressive experience.";
            } else if (review.getRating() == 4) {
                apologyVi = "Cảm ơn quý khách rất nhiều! Chúng tôi rất vui khi quý khách hài lòng với trải nghiệm tại nhà hàng."
                        + "|||" + "Cảm ơn quý khách đã đánh giá! Quý khách có thể cho chúng tôi biết điều gì chưa hoàn toàn như ý để chúng tôi chinh phục 5 sao từ quý khách không?"
                        + "|||" + "Rất cảm ơn quý khách! Đội ngũ chúng tôi tự hào về sự hài lòng của quý khách và mong được phục vụ quý khách lần sau.";
                apologyEn = "Thank you so much! We are glad you had a great experience at our restaurant."
                        + "|||" + "Thank you for your rating! Could you share what we could do better to earn that perfect 5-star rating from you?"
                        + "|||" + "We truly appreciate your feedback! Our team takes pride in your satisfaction and looks forward to serving you again.";
            } else {
                apologyVi = "Cảm ơn quý khách rất nhiều! Chúng tôi tự hào khi quý khách có trải nghiệm tuyệt vời tại nhà hàng."
                        + "|||" + "Cảm ơn quý khách đã dành thời gian chia sẻ. Chúng tôi rất vui và mong quý khách sẽ tiếp tục ủng hộ và giới thiệu bạn bè!"
                        + "|||" + "Chúng tôi rất cảm kích đánh giá tuyệt vời này! Đội ngũ nhà hàng cam kết duy trì và không ngừng nâng cao chất lượng phục vụ.";
                apologyEn = "Thank you so much! We are proud that you had an excellent experience at our restaurant."
                        + "|||" + "Thank you for taking the time to share your experience. We hope you'll continue to support us and recommend us to your friends!"
                        + "|||" + "We truly appreciate this wonderful review! Our team is committed to maintaining and continuously improving our quality of service.";
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
