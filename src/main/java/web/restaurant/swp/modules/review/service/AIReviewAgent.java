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

        // Build prompt based on actual review content
        String customerName = review.getCustomerName();
        String comment = review.getComment() != null ? review.getComment() : "";
        int rating = review.getRating();

        // Generate 3 DIVERSE, PERSONALIZED suggestions based on what customer actually said
        String prompt = "Bạn là nhân viên chăm sóc khách hàng chuyên nghiệp của một nhà hàng cao cấp.\n"
                + "Nhiệm vụ: Viết 3 câu trả lời THẬT SỰ KHÁC NHAU về giọng điệu cho đánh giá sau.\n\n"
                + "=== THÔNG TIN ĐÁNH GIÁ ===\n"
                + "Tên khách hàng: " + customerName + "\n"
                + "Số sao: " + rating + "/5\n"
                + "Nội dung bình luận: \"" + (comment.isEmpty() ? "(Không viết bình luận)" : comment) + "\"\n"
                + "Món đã gọi: " + orderItemsText + "\n"
                + "Tổng hóa đơn: " + orderTotal + " VNĐ\n\n"
                + "=== YÊU CẦU QUAN TRỌNG ===\n"
                + "1. Mỗi câu trả lời PHẢI ĐỀ CẬP CỤ THỂ đến nội dung bình luận của khách (đề cập đến món ăn, không gian, dịch vụ... mà khách đã nhắc đến).\n"
                + "2. 3 câu phải KHÁC NHAU HOÀN TOÀN về:\n"
                + "   - Cách mở đầu (không dùng cùng một từ đầu tiên)\n"
                + "   - Giọng điệu (thân thiện/trịnh trọng/hài hước nhẹ)\n"
                + "   - Nội dung nhấn mạnh khác nhau\n"
                + "3. TUYỆT ĐỐI KHÔNG dùng câu chung chung như 'Cảm ơn bạn đã đánh giá. Rất mong được phục vụ bạn lần sau'.\n"
                + "4. Độ dài: mỗi câu 1-3 câu, ngắn gọn, tự nhiên.\n\n"
                + (rating >= 4
                    ? "Hướng dẫn theo mức độ đánh giá (" + rating + " sao - TÍCH CỰC):\n"
                    + "  - Câu 1 (Cảm ơn nhiệt tình): Cảm ơn chân thành, nhắc lại điều khách khen cụ thể\n"
                    + "  - Câu 2 (Tự hào chia sẻ): Tự hào về món/dịch vụ khách khen, mời giới thiệu bạn bè\n"
                    + "  - Câu 3 (Cam kết chất lượng): Cam kết duy trì và cải thiện, hẹn gặp lại\n"
                    : rating == 3
                    ? "Hướng dẫn theo mức độ đánh giá (" + rating + " sao - TRUNG BÌNH):\n"
                    + "  - Câu 1 (Ghi nhận cân bằng): Cảm ơn, ghi nhận điểm tốt và điểm cần cải thiện\n"
                    + "  - Câu 2 (Hỏi thêm): Hỏi cụ thể điều gì chưa vừa ý để cải thiện\n"
                    + "  - Câu 3 (Hứa cải thiện): Cảm ơn và hứa khắc phục, mời trải nghiệm lại\n"
                    : "Hướng dẫn theo mức độ đánh giá (" + rating + " sao - TIÊU CỰC):\n"
                    + "  - Câu 1 (Xin lỗi chân thành): Xin lỗi sâu sắc về vấn đề cụ thể khách nêu ra\n"
                    + "  - Câu 2 (Hỏi để giải quyết): Hỏi thêm chi tiết về vấn đề, đề nghị hỗ trợ trực tiếp\n"
                    + "  - Câu 3 (Cam kết khắc phục): Hứa khắc phục ngay và mời quay lại với ưu đãi\n"
                )
                + "\n=== YÊU CẦU FORMAT ===\n"
                + "Chỉ trả về JSON thuần túy, không có markdown, không có text ngoài JSON:\n"
                + "{\n"
                + "  \"sentiment\": \"POSITIVE\" | \"NEGATIVE\" | \"NEUTRAL\",\n"
                + "  \"suggestion1Vi\": \"câu 1 tiếng Việt...\",\n"
                + "  \"suggestion2Vi\": \"câu 2 tiếng Việt...\",\n"
                + "  \"suggestion3Vi\": \"câu 3 tiếng Việt...\",\n"
                + "  \"suggestion1En\": \"reply 1 in English...\",\n"
                + "  \"suggestion2En\": \"reply 2 in English...\",\n"
                + "  \"suggestion3En\": \"reply 3 in English...\",\n"
                + "  \"voucherType\": \"PercentDiscount\" | \"FlatDiscount\" | \"None\",\n"
                + "  \"voucherValue\": 0,\n"
                + "  \"reasonForValue\": \"reason...\"\n"
                + "}";



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
                        // Extract Gemini raw text
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode root = mapper.readTree(body);
                        String rawText = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();

                        // Strip markdown code fences if present
                        String jsonResponse = rawText.trim();
                        if (jsonResponse.contains("```json")) {
                            jsonResponse = jsonResponse.substring(jsonResponse.indexOf("```json") + 7);
                            int fence = jsonResponse.indexOf("```");
                            if (fence != -1) jsonResponse = jsonResponse.substring(0, fence);
                        } else if (jsonResponse.startsWith("```")) {
                            jsonResponse = jsonResponse.substring(3);
                            int fence = jsonResponse.indexOf("```");
                            if (fence != -1) jsonResponse = jsonResponse.substring(0, fence);
                        }
                        // Find JSON object boundaries
                        int jsonStart = jsonResponse.indexOf('{');
                        int jsonEnd = jsonResponse.lastIndexOf('}');
                        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                            jsonResponse = jsonResponse.substring(jsonStart, jsonEnd + 1);
                        }
                        jsonResponse = jsonResponse.trim();

                        // Use Jackson for robust JSON parsing (handles escaped chars, commas, etc.)
                        JsonNode parsed = mapper.readTree(jsonResponse);

                        if (parsed.has("sentiment")) {
                            parsedSentiment = parsed.get("sentiment").asText(parsedSentiment).toUpperCase();
                        }

                        // Parse 3 suggestions using Jackson (safe for any content)
                        String s1Vi = parsed.has("suggestion1Vi") ? parsed.get("suggestion1Vi").asText("") : "";
                        String s2Vi = parsed.has("suggestion2Vi") ? parsed.get("suggestion2Vi").asText("") : "";
                        String s3Vi = parsed.has("suggestion3Vi") ? parsed.get("suggestion3Vi").asText("") : "";
                        String s1En = parsed.has("suggestion1En") ? parsed.get("suggestion1En").asText("") : "";
                        String s2En = parsed.has("suggestion2En") ? parsed.get("suggestion2En").asText("") : "";
                        String s3En = parsed.has("suggestion3En") ? parsed.get("suggestion3En").asText("") : "";

                        // Combine non-empty suggestions with ||| separator
                        java.util.List<String> viParts = new java.util.ArrayList<>();
                        java.util.List<String> enParts = new java.util.ArrayList<>();
                        if (!s1Vi.isBlank()) viParts.add(s1Vi.trim());
                        if (!s2Vi.isBlank()) viParts.add(s2Vi.trim());
                        if (!s3Vi.isBlank()) viParts.add(s3Vi.trim());
                        if (!s1En.isBlank()) enParts.add(s1En.trim());
                        if (!s2En.isBlank()) enParts.add(s2En.trim());
                        if (!s3En.isBlank()) enParts.add(s3En.trim());

                        if (!viParts.isEmpty()) apologyVi = String.join("|||", viParts);
                        if (!enParts.isEmpty()) apologyEn = String.join("|||", enParts);

                        if (parsed.has("voucherType")) vType = parsed.get("voucherType").asText(vType);
                        if (parsed.has("voucherValue")) {
                            try { vValue = parsed.get("voucherValue").asDouble(0.0); } catch (Exception ignored) {}
                        }
                        log.info("AI Review Agent: parsed {} VI suggestions and {} EN suggestions for review {}",
                                viParts.size(), enParts.size(), review.getId());
                    } catch (Exception parseEx) {
                        log.error("Failed to parse Gemini JSON response for review {}: {}", review.getId(), parseEx.getMessage());
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
