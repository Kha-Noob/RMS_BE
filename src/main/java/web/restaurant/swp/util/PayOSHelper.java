package web.restaurant.swp.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Component
public class PayOSHelper {

    @Value("${payos.client-id}")
    private String clientId;

    @Value("${payos.api-key}")
    private String apiKey;

    @Value("${payos.checksum-key}")
    private String checksumKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public static String hmacSha256(String data, String key) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to calculate HMAC SHA256", e);
        }
    }

    public static long generateOrderCode() {
        long timestamp = System.currentTimeMillis();
        int random = new Random().nextInt(900) + 100;
        // Generate a 12-digit positive number that fits into int64/Long
        return Long.parseLong(String.valueOf(timestamp).substring(4) + random);
    }

    public Map<String, Object> createPaymentLink(long orderCode, double amount, String description, String returnUrl, String cancelUrl) {
        int intAmount = (int) amount;
        
        Map<String, Object> sortedParams = new TreeMap<>();
        sortedParams.put("amount", intAmount);
        sortedParams.put("cancelUrl", cancelUrl);
        sortedParams.put("description", description);
        sortedParams.put("orderCode", orderCode);
        sortedParams.put("returnUrl", returnUrl);

        StringBuilder signData = new StringBuilder();
        for (Map.Entry<String, Object> entry : sortedParams.entrySet()) {
            if (!signData.isEmpty()) {
                signData.append("&");
            }
            signData.append(entry.getKey()).append("=").append(entry.getValue());
        }

        String signature = hmacSha256(signData.toString(), checksumKey);

        Map<String, Object> requestBody = new LinkedHashMap<>(sortedParams);
        requestBody.put("signature", signature);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", clientId);
        headers.set("x-api-key", apiKey);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://api-merchant.payos.vn/v2/payment-requests",
                    requestEntity,
                    Map.class
            );
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map responseBody = response.getBody();
                if ("00".equals(responseBody.get("code"))) {
                    return (Map<String, Object>) responseBody.get("data");
                } else {
                    throw new RuntimeException("PayOS error: " + responseBody.get("desc"));
                }
            }
            throw new RuntimeException("PayOS request failed with status: " + response.getStatusCode());
        } catch (Exception e) {
            throw new RuntimeException("Failed to call PayOS API: " + e.getMessage(), e);
        }
    }

    public boolean verifyWebhookSignature(Map<String, Object> payload) {
        if (payload == null || !payload.containsKey("signature") || !payload.containsKey("data")) {
            return false;
        }

        String signature = (String) payload.get("signature");
        Map<String, Object> data = (Map<String, Object>) payload.get("data");

        Map<String, Object> sortedData = new TreeMap<>(data);
        StringBuilder signData = new StringBuilder();
        for (Map.Entry<String, Object> entry : sortedData.entrySet()) {
            if (!signData.isEmpty()) {
                signData.append("&");
            }
            signData.append(entry.getKey()).append("=").append(entry.getValue());
        }

        String calculatedSignature = hmacSha256(signData.toString(), checksumKey);
        return calculatedSignature.equalsIgnoreCase(signature);
    }
}
