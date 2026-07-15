package web.restaurant.swp.modules.upload.model;

import lombok.Data;

@Data
public class PresignRequest {
    private String module;
    private String purpose;
    private String fileName;
    private String contentType;
    private Long size;
    private Long floorPlanId;
}
