package web.restaurant.swp.modules.analytics.service;

import web.restaurant.swp.modules.auth.model.*;
import web.restaurant.swp.modules.auth.repository.*;
import web.restaurant.swp.modules.auth.service.*;
import web.restaurant.swp.modules.pos.model.*;
import web.restaurant.swp.modules.pos.repository.*;
import web.restaurant.swp.modules.pos.service.*;
import web.restaurant.swp.modules.inventory.model.*;
import web.restaurant.swp.modules.inventory.repository.*;
import web.restaurant.swp.modules.inventory.service.*;
import web.restaurant.swp.modules.procurement.model.*;
import web.restaurant.swp.modules.procurement.repository.*;
import web.restaurant.swp.modules.procurement.service.*;
import web.restaurant.swp.modules.hr.model.*;
import web.restaurant.swp.modules.hr.repository.*;
import web.restaurant.swp.modules.hr.service.*;
import web.restaurant.swp.modules.loyalty.model.*;
import web.restaurant.swp.modules.loyalty.repository.*;
import web.restaurant.swp.modules.loyalty.service.*;
import web.restaurant.swp.modules.promotion.model.*;
import web.restaurant.swp.modules.promotion.repository.*;
import web.restaurant.swp.modules.promotion.service.*;
import web.restaurant.swp.modules.analytics.service.*;
import web.restaurant.swp.modules.branch.model.*;
import web.restaurant.swp.modules.branch.repository.*;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIService {
    private final OrderRepository orderRepository;
    private final BranchInventoryRepository branchInventoryRepository;

    @Value("${openai.api.key:}")
    private String apiKey;

    public String analyzeDailyReport(String branchId, String query) {
        // Collect current stats to feed to the AI context
        List<Order> orders = orderRepository.findByBranchId(branchId);
        double totalRevenue = orders.stream()
                .filter(o -> "SERVED".equalsIgnoreCase(o.getStatus()))
                .mapToDouble(Order::getTotalAmount)
                .sum();
        long completedCount = orders.stream()
                .filter(o -> "SERVED".equalsIgnoreCase(o.getStatus()))
                .count();

        List<BranchInventory> lowStocks = branchInventoryRepository.findByBranchBranchId(branchId).stream()
                .filter(b -> b.getQuantity() <= b.getReorderPoint())
                .toList();

        StringBuilder dataContext = new StringBuilder();
        dataContext.append("Doanh thu hôm nay: ").append(totalRevenue).append(" VNĐ.\n");
        dataContext.append("Số đơn hoàn thành: ").append(completedCount).append(" đơn.\n");
        dataContext.append("Nguyên liệu cảnh báo tồn kho thấp: ").append(lowStocks.size()).append(" mặt hàng.\n");
        for (BranchInventory b : lowStocks) {
            dataContext.append("- ").append(b.getItem().getName()).append(": Còn ").append(b.getQuantity()).append(" ").append(b.getItem().getUnit()).append("\n");
        }

        if (apiKey != null && !apiKey.trim().isEmpty()) {
            try {
                // Perform actual call to OpenAI Chat Completion endpoint
                String prompt = "You are LiteFlow AI, an assistant for restaurant managers. Analyze the following data and respond in Vietnamese:\n"
                        + dataContext.toString() + "\nUser asks: " + query;

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
                    // Extract response content
                    String body = response.body();
                    int textStart = body.indexOf("\"text\": \"") + 9;
                    int textEnd = body.indexOf("\"", textStart);
                    if (textStart > 8 && textEnd > textStart) {
                        return body.substring(textStart, textEnd).replace("\\n", "\n").replace("\\\"", "\"");
                    }

                }
            } catch (Exception e) {
                log.error("Error calling OpenAI API, falling back to local analysis", e);
            }
        }

        // Sleek Fallback local analysis
        return "### LiteFlow AI - Tóm tắt vận hành trong ngày\n"
                + "* **Tổng doanh thu:** " + String.format("%,.0f", totalRevenue) + " VNĐ\n"
                + "* **Số lượng giao dịch:** " + completedCount + " đơn phục vụ thành công.\n"
                + "* **Cảnh báo tồn kho:** Có " + lowStocks.size() + " nguyên liệu cần bổ sung gấp:\n"
                + (lowStocks.isEmpty() ? "  - Không có cảnh báo tồn kho thấp. Vận hành ổn định.\n" : "")
                + getInventoryDetailsString(lowStocks)
                + "\n* **Đề xuất vận hành:**\n"
                + "  1. Kiểm tra nhà cung cấp và tạo phiếu đặt hàng PO cho nguyên liệu sắp hết để tránh gián đoạn phục vụ.\n"
                + "  2. Tập trung quảng bá các món ăn bán chạy thông qua Promotion Engine để tối ưu doanh số tối nay.";
    }

    private String getInventoryDetailsString(List<BranchInventory> lowStocks) {
        StringBuilder sb = new StringBuilder();
        for (BranchInventory b : lowStocks) {
            sb.append("  - **").append(b.getItem().getName()).append("**: ").append(b.getQuantity()).append(" / ").append(b.getReorderPoint()).append(" ").append(b.getItem().getUnit()).append(" (Đã chạm ngưỡng tối thiểu)\n");
        }
        return sb.toString();
    }

    public String checkAllergens(List<Product> products, String allergenQuery) {
        StringBuilder menuContext = new StringBuilder();
        menuContext.append("Danh sách thực đơn và thành phần món ăn:\n");
        for (Product p : products) {
            menuContext.append("- ID: ").append(p.getId())
                       .append(" | Tên món: ").append(p.getName())
                       .append(" | Thành phần: ").append(p.getIngredients() != null ? p.getIngredients() : "Không ghi rõ")
                       .append(" | Mô tả: ").append(p.getDescription() != null ? p.getDescription() : "")
                       .append("\n");
        }

        String prompt = "Bạn là Trợ lý AI Bảo vệ Khách hàng khỏi Dị ứng Thực phẩm của nhà hàng. "
                + "Khách hàng thông báo họ bị dị ứng hoặc muốn tránh các thành phần sau: \"" + allergenQuery + "\".\\n"
                + "Hãy phân tích danh sách thực đơn dưới đây và trả về kết quả định dạng JSON với cấu trúc chính xác như sau (không kèm mã markdown hay từ khóa khác, chỉ trả về chuỗi JSON thô hợp lệ):\\n"
                + "{\\n"
                + "  \\\"allergenQuery\\\": \\\"" + allergenQuery.replace("\"", "\\\"") + "\\\",\\n"
                + "  \\\"dishes\\\": [\\n"
                + "    {\\n"
                + "      \\\"productId\\\": 1,\\n"
                + "      \\\"productName\\\": \\\"Tên món ăn\\\",\\n"
                + "      \\\"status\\\": \\\"SAFE\\\" hoặc \\\"WARNING\\\",\\n"
                + "      \\\"reason\\\": \\\"Giải thích chi tiết tại sao an toàn hoặc cảnh báo lây nhiễm chéo/thành phần gây dị ứng\\\",\\n"
                + "      \\\"suggestedSubstitute\\\": \\\"Tên món ăn thay thế an toàn nếu có, hoặc để trống\\\"\\n"
                + "    }\\n"
                + "  ]\\n"
                + "}\\n\\n"
                + menuContext.toString();

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
                        return jsonResponse.trim();
                    }
                }
            } catch (Exception e) {
                log.error("Error calling Gemini API for allergen check", e);
            }
        }

        StringBuilder fallback = new StringBuilder();
        fallback.append("{\n");
        fallback.append("  \"allergenQuery\": \"").append(allergenQuery).append("\",\n");
        fallback.append("  \"dishes\": [\n");
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            boolean hasAllergen = false;
            String reason = "Món ăn an toàn, không chứa thành phần dị ứng.";
            if (p.getIngredients() != null && p.getIngredients().toLowerCase().contains(allergenQuery.toLowerCase())) {
                hasAllergen = true;
                reason = "Phát hiện thành phần dị ứng \"" + allergenQuery + "\" trong mô tả nguyên liệu.";
            } else if (p.getDescription() != null && p.getDescription().toLowerCase().contains(allergenQuery.toLowerCase())) {
                hasAllergen = true;
                reason = "Mô tả món ăn có đề cập đến \"" + allergenQuery + "\".";
            }
            fallback.append("    {\n")
                    .append("      \"productId\": ").append(p.getId()).append(",\n")
                    .append("      \"productName\": \"").append(p.getName()).append("\",\n")
                    .append("      \"status\": \"").append(hasAllergen ? "WARNING" : "SAFE").append("\",\n")
                    .append("      \"reason\": \"").append(reason).append("\",\n")
                    .append("      \"suggestedSubstitute\": \"\"\n")
                    .append("    }").append(i < products.size() - 1 ? ",\n" : "\n");
        }
        fallback.append("  ]\n");
        fallback.append("}");
        return fallback.toString();
    }
}
