package web.restaurant.swp.modules.upload.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class PresignResponse {
    private String uploadUrl;
    private String fileKey;
    private String publicUrl;
    private String method;
    private Map<String, String> headers;
}
