package web.restaurant.swp.modules.review.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@Service
@Slf4j
public class AIContentService {

    @Value("${openai.api.key:}")
    private String apiKey;

    private String cleanGeminiJson(String text) {
        if (text == null) return "{}";
        String cleaned = text.trim();
        if (cleaned.contains("```json")) {
            cleaned = cleaned.substring(cleaned.indexOf("```json") + 7);
            if (cleaned.contains("```")) {
                cleaned = cleaned.substring(0, cleaned.indexOf("```"));
            }
        } else if (cleaned.contains("```")) {
            cleaned = cleaned.substring(cleaned.indexOf("```") + 3);
            if (cleaned.contains("```")) {
                cleaned = cleaned.substring(0, cleaned.indexOf("```"));
            }
        }
        return cleaned.trim();
    }

    public Map<String, Object> generatePostContent(String userPrompt) {
        log.info("Generating AI content for prompt: {}", userPrompt);

        String systemPrompt = "Bạn là Trợ lý viết bài ẩm thực AI chuyên nghiệp cho chuỗi nhà hàng.\n"
                + "Dựa trên ý tưởng/từ khóa sau của người dùng: \"" + userPrompt + "\", hãy viết một bài viết quảng cáo hoàn chỉnh chuẩn SEO, lôi cuốn người đọc.\n"
                + "Hãy trả về kết quả cấu trúc JSON chính xác như sau (chỉ trả về JSON thô hợp lệ, không chứa các định dạng markdown khác):\n"
                + "{\n"
                + "  \"title\": \"Tiêu đề bài viết gợi ý hấp dẫn\",\n"
                + "  \"content\": \"Nội dung bài viết chi tiết, phân đoạn rõ ràng, có lời mở đầu, phần mô tả đặc sắc và lời kêu gọi hành động (Call To Action)...\",\n"
                + "  \"hashtags\": \"#goicantrich #monngonmuahe #nhahang\"\n"
                + "}\n";

        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Map.of(
                "title", "Gợi ý tiêu đề: Trải nghiệm ẩm thực hấp dẫn",
                "content", "Đây là nội dung mẫu vì khóa API Gemini chưa được cấu hình. Ý tưởng của bạn: " + userPrompt,
                "hashtags", "#amthuc #nhahang #gocphathanh"
            );
        }

        try {
            String requestBody = "{"
                    + "\"contents\": [{"
                    + "\"parts\": [{"
                    + "\"text\": \"" + systemPrompt.replace("\n", "\\n").replace("\"", "\\\"") + "\""
                    + "}]"
                    + "}]"
                    + "}";

            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey))
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
                    String jsonStr = cleanGeminiJson(rawText);
                    
                    // Simple parse of the JSON keys
                    String title = extractJsonField(jsonStr, "title", "Gợi ý: Khám phá hương vị mới");
                    String content = extractJsonField(jsonStr, "content", "Nội dung đang được cập nhật...");
                    String hashtags = extractJsonField(jsonStr, "hashtags", "#amthuc");

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("title", title);
                    result.put("content", content);
                    result.put("hashtags", hashtags);
                    return result;
                } catch (Exception parseEx) {
                    log.error("Failed to parse Gemini JSON response via Jackson", parseEx);
                }
            } else {
                log.warn("Gemini API call failed with status: {}, response: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Error calling Gemini API for text generation", e);
        }

        return Map.of(
            "title", "Gợi ý: Trải nghiệm thực đơn mới",
            "content", "Có lỗi xảy ra khi gọi AI. Ý tưởng của bạn: " + userPrompt,
            "hashtags", "#amthuc #delicacy"
        );
    }

    public Map<String, Object> analyzeCropBox(byte[] imageBytes, String mimeType) {
        log.info("AI Crop Box analysis for image (size: {} bytes)", imageBytes.length);
        
        String prompt = "Bạn là chuyên gia thiết kế đồ họa & thị giác máy tính.\n"
                + "Hãy phân tích bức ảnh này và đề xuất một vùng cắt (crop box) hình chữ nhật tối ưu để làm banner sự kiện tỉ lệ rộng 16:9.\n"
                + "Vùng cắt cần đảm bảo giữ lại toàn bộ chữ viết chính, logo hoặc khuôn mặt của đối tượng chính trong ảnh, tránh bị cắt xén mất chữ.\n"
                + "Bức ảnh có kích thước giả định là 1000x1000. Hãy trả về tọa độ tỉ lệ từ 0 đến 1000.\n"
                + "Hãy trả về kết quả cấu trúc JSON chính xác như sau (chỉ trả về JSON thô hợp lệ, không chứa các định dạng markdown khác):\n"
                + "{\n"
                + "  \"x\": tọa độ x bắt đầu (từ 0 đến 1000),\n"
                + "  \"y\": tọa độ y bắt đầu (từ 0 đến 1000),\n"
                + "  \"width\": chiều rộng vùng cắt (từ 0 đến 1000),\n"
                + "  \"height\": chiều cao vùng cắt (từ 0 đến 1000),\n"
                + "  \"reason\": \"Giải thích lý do lựa chọn tọa độ này...\"\n"
                + "}\n";

        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Map.of(
                "x", 0,
                "y", 125,
                "width", 1000,
                "height", 562,
                "reason", "Mặc định cắt chính giữa tỉ lệ 16:9 vì API Key chưa cấu hình."
            );
        }

        try {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String requestBody = "{"
                    + "\"contents\": [{"
                    + "\"parts\": ["
                    + "{\"text\": \"" + prompt.replace("\n", "\\n").replace("\"", "\\\"") + "\"},"
                    + "{\"inlineData\": {\"mimeType\": \"" + mimeType + "\", \"data\": \"" + base64Image + "\"}}"
                    + "]"
                    + "}]"
                    + "}";

            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey))
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
                    String jsonStr = cleanGeminiJson(rawText);
                    
                    String xVal = extractJsonField(jsonStr, "x", "0");
                    String yVal = extractJsonField(jsonStr, "y", "125");
                    String wVal = extractJsonField(jsonStr, "width", "1000");
                    String hVal = extractJsonField(jsonStr, "height", "562");
                    String reason = extractJsonField(jsonStr, "reason", "Cắt tỉ lệ 16:9 tối ưu.");

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("x", Integer.parseInt(xVal.replaceAll("[^0-9]", "")));
                    result.put("y", Integer.parseInt(yVal.replaceAll("[^0-9]", "")));
                    result.put("width", Integer.parseInt(wVal.replaceAll("[^0-9]", "")));
                    result.put("height", Integer.parseInt(hVal.replaceAll("[^0-9]", "")));
                    result.put("reason", reason);
                    return result;
                } catch (Exception parseEx) {
                    log.error("Failed to parse Gemini JSON response via Jackson", parseEx);
                }
            } else {
                log.warn("Gemini API call failed with status: {}, response: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Error calling Gemini API for multimodal crop box detection", e);
        }

        return Map.of(
            "x", 0,
            "y", 125,
            "width", 1000,
            "height", 562,
            "reason", "Cắt giữa ảnh tỉ lệ 16:9 do có lỗi trong quá trình phân tích."
        );
    }

    public Map<String, Object> generateUiThemeConfig(String userPrompt) {
        return generateUiThemeConfig(userPrompt, null);
    }

    public Map<String, Object> generateUiThemeConfig(String userPrompt, Map<String, Object> currentSettings) {
        log.info("Generating AI UI design for prompt: {} with current settings: {}", userPrompt, currentSettings);

        String currentContextText = "";
        if (currentSettings != null && !currentSettings.isEmpty()) {
            currentContextText = "\nCấu hình giao diện hiện tại của nhà hàng:\n"
                    + "- Tên hiển thị: " + currentSettings.getOrDefault("restaurantName", "") + "\n"
                    + "- Lời giới thiệu/Mô tả hiện tại: " + currentSettings.getOrDefault("description", "") + "\n"
                    + "- Màu chủ đạo: " + currentSettings.getOrDefault("primaryColor", "") + "\n"
                    + "- Màu phụ: " + currentSettings.getOrDefault("secondaryColor", "") + "\n"
                    + "- Màu nền: " + currentSettings.getOrDefault("backgroundColor", "") + "\n"
                    + "- Màu chữ chính: " + currentSettings.getOrDefault("textColor", "") + "\n"
                    + "- Font chữ: " + currentSettings.getOrDefault("fontFamily", "") + "\n"
                    + "- Phong cách bố cục: " + currentSettings.getOrDefault("layoutStyle", "") + "\n"
                    + "- Link ảnh bìa: " + currentSettings.getOrDefault("coverImageUrl", "") + "\n\n"
                    + "Yêu cầu chỉnh sửa/cập nhật giao diện của người dùng: \"" + userPrompt + "\".\n"
                    + "Hãy cập nhật cấu hình dựa trên yêu cầu này. Hãy giữ nguyên các thuộc tính cũ của cấu hình trừ khi yêu cầu của người dùng muốn thay đổi hoặc ngụ ý thay đổi chúng. Ví dụ nếu người dùng yêu cầu 'đổi màu chữ chính sang màu đỏ', hãy CHỈ thay đổi màu chữ chính trong JSON kết quả và giữ nguyên toàn bộ các giá trị màu sắc, font, mô tả khác từ cấu hình hiện tại.";
        }

        String systemPrompt = "Bạn là Trợ lý Thiết kế Giao diện AI chuyên nghiệp cho chuỗi nhà hàng.\n"
                + (currentContextText.isEmpty()
                    ? "Dựa trên yêu cầu/ý tưởng phong cách thiết kế sau của người dùng: \"" + userPrompt + "\", hãy đề xuất các thuộc tính giao diện phù hợp.\n"
                    : currentContextText)
                + "Hãy trả về kết quả cấu trúc JSON chính xác như sau (chỉ trả về JSON thô hợp lệ, không chứa các định dạng markdown khác):\n"
                + "{\n"
                + "  \"themeName\": \"Tên phong cách gợi ý hoặc tóm tắt thay đổi giao diện ngắn gọn bằng tiếng Việt (Ví dụ: Classic French, Neon Ramen, Vintage Coffee, Thay đổi màu chữ)\",\n"
                + "  \"primaryColor\": \"Mã màu hex chủ đạo (Ví dụ: #ff4500)\",\n"
                + "  \"secondaryColor\": \"Mã màu hex phụ (Ví dụ: #ff8c00)\",\n"
                + "  \"backgroundColor\": \"Mã màu hex nền (Ví dụ: #fcfcfc)\",\n"
                + "  \"textColor\": \"Mã màu hex chữ chính (Ví dụ: #1a1a1a)\",\n"
                + "  \"fontFamily\": \"Tên Font chữ từ Google Fonts (Ví dụ: Inter, Playfair Display, Roboto, Courier Prime, Great Vibes, Montserrat)\",\n"
                + "  \"layoutStyle\": \"Bố cục giao diện: một trong các giá trị [modern, classic, minimal, warm]\",\n"
                + "  \"coverImageCuisineKeyword\": \"Từ khóa tiếng Anh ngắn để tìm ảnh bìa ẩm thực tương ứng (Ví dụ: 'french cuisine', 'ramen neon', 'coffee shop vintage'). Trả về rỗng \"\" nếu muốn giữ nguyên ảnh bìa hiện tại.\",\n"
                + "  \"welcomeTitle\": \"Tiêu đề chào mừng hấp dẫn phù hợp phong cách hoặc giữ nguyên tiêu đề cũ\",\n"
                + "  \"welcomeDescription\": \"Đoạn văn ngắn (2-3 câu) giới thiệu/mô tả nhà hàng phù hợp phong cách thiết kế được cập nhật hoặc giữ nguyên mô tả cũ\"\n"
                + "}\n";

        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Map.of(
                "themeName", "Phong cách Mặc định",
                "primaryColor", "#25439b",
                "secondaryColor", "#3b82f6",
                "backgroundColor", "#ffffff",
                "textColor", "#0f172a",
                "fontFamily", "Inter",
                "layoutStyle", "modern",
                "coverImageCuisineKeyword", "restaurant food",
                "welcomeTitle", "Chào mừng bạn đến với nhà hàng chúng tôi",
                "welcomeDescription", "Đây là cấu hình mẫu vì khóa API Gemini chưa được cấu hình. Ý tưởng của bạn: " + userPrompt
            );
        }

        try {
            String requestBody = "{"
                    + "\"contents\": [{"
                    + "\"parts\": [{"
                    + "\"text\": \"" + systemPrompt.replace("\n", "\\n").replace("\"", "\\\"") + "\""
                    + "}]"
                    + "}]"
                    + "}";

            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey))
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
                    String jsonStr = cleanGeminiJson(rawText);
                    
                    String themeName = extractJsonField(jsonStr, "themeName", "Phong cách Gợi ý");
                    String primaryColor = extractJsonField(jsonStr, "primaryColor", "#25439b");
                    String secondaryColor = extractJsonField(jsonStr, "secondaryColor", "#3b82f6");
                    String backgroundColor = extractJsonField(jsonStr, "backgroundColor", "#ffffff");
                    String textColor = extractJsonField(jsonStr, "textColor", "#0f172a");
                    String fontFamily = extractJsonField(jsonStr, "fontFamily", "Inter");
                    String layoutStyle = extractJsonField(jsonStr, "layoutStyle", "modern");
                    String coverImageCuisineKeyword = extractJsonField(jsonStr, "coverImageCuisineKeyword", "restaurant food");
                    String welcomeTitle = extractJsonField(jsonStr, "welcomeTitle", "Chào mừng");
                    String welcomeDescription = extractJsonField(jsonStr, "welcomeDescription", "Mô tả nhà hàng...");

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("themeName", themeName);
                    result.put("primaryColor", primaryColor);
                    result.put("secondaryColor", secondaryColor);
                    result.put("backgroundColor", backgroundColor);
                    result.put("textColor", textColor);
                    result.put("fontFamily", fontFamily);
                    result.put("layoutStyle", layoutStyle);
                    result.put("coverImageCuisineKeyword", coverImageCuisineKeyword);
                    result.put("welcomeTitle", welcomeTitle);
                    result.put("welcomeDescription", welcomeDescription);
                    return result;
                } catch (Exception parseEx) {
                    log.error("Failed to parse Gemini JSON response via Jackson", parseEx);
                }
            } else {
                log.warn("Gemini API call failed with status: {}, response: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Error calling Gemini API for UI theme generation", e);
        }

        return Map.of(
            "themeName", "Phong cách Mặc định",
            "primaryColor", "#25439b",
            "secondaryColor", "#3b82f6",
            "backgroundColor", "#ffffff",
            "textColor", "#0f172a",
            "fontFamily", "Inter",
            "layoutStyle", "modern",
            "coverImageCuisineKeyword", "restaurant food",
            "welcomeTitle", "Chào mừng",
            "welcomeDescription", "Không thể gọi API Gemini. Đây là nội dung mặc định."
        );
    }

    private String extractJsonField(String json, String field, String defaultValue) {
        String pattern = "\"" + field + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) return defaultValue;
        int start = idx + pattern.length();
        while (start < json.length() && (Character.isWhitespace(json.charAt(start)) || json.charAt(start) == ':')) {
            start++;
        }
        if (start >= json.length()) return defaultValue;
        if (json.charAt(start) == '"') {
            start++;
            int end = json.indexOf("\"", start);
            if (end != -1) {
                return json.substring(start, end);
            }
        } else {
            // number or boolean
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-' || json.charAt(end) == '+')) {
                end++;
            }
            if (end > start) {
                return json.substring(start, end);
            }
        }
        return defaultValue;
    }
}
