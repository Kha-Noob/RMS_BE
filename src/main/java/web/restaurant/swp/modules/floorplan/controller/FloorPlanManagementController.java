package web.restaurant.swp.modules.floorplan.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.UserRepository;
import web.restaurant.swp.modules.branch.service.BranchAccessService;
import web.restaurant.swp.modules.floorplan.model.FloorPlan;
import web.restaurant.swp.modules.floorplan.model.FloorPlanObject;
import web.restaurant.swp.modules.floorplan.service.FloorPlanService;
import web.restaurant.swp.modules.upload.model.PresignRequest;
import web.restaurant.swp.modules.upload.model.PresignResponse;
import web.restaurant.swp.modules.upload.service.UploadService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class FloorPlanManagementController {

    private final FloorPlanService floorPlanService;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;
    private final UploadService uploadService;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    // ─── Helpers ──────────────────────────────────────────────────────

    private User getLoggedInUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private void requireAdminOrManager(User user) {
        if (user == null || user.getRoles().stream()
                .noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "MANAGER".equalsIgnoreCase(r.getName()))) {
            throw new SecurityException("Không có quyền thực hiện thao tác này");
        }
    }

    private ResponseEntity<?> message(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    // Floor Plan List

    private boolean isAdminOrManager(User user) {
        return user != null && user.getRoles().stream()
                .anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "MANAGER".equalsIgnoreCase(r.getName()));
    }

    private void validateDirectImageUpload(Long floorPlanId, String purpose, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        PresignRequest request = new PresignRequest();
        request.setModule("floor-plans");
        request.setPurpose(purpose);
        request.setFloorPlanId(floorPlanId);
        request.setFileName(file.getOriginalFilename());
        request.setContentType(file.getContentType());
        request.setSize(file.getSize());
        uploadService.validateImageFile(request);
    }

    private String saveUploadedFile(MultipartFile file, String branchId, String subDir) throws IOException {
        if (file.isEmpty()) throw new RuntimeException("File không được để trống");

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) throw new RuntimeException("Tên file không hợp lệ");

        String ext = "";
        int dotIdx = originalFilename.lastIndexOf('.');
        if (dotIdx >= 0) ext = originalFilename.substring(dotIdx + 1).toLowerCase();

        Set<String> allowedTypes = Set.of("jpg", "jpeg", "png", "webp", "gif");
        if (!allowedTypes.contains(ext)) {
            throw new RuntimeException("Chỉ chấp nhận file ảnh: jpg, jpeg, png, webp, gif");
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new RuntimeException("File tối đa 10MB");
        }

        String filename = UUID.randomUUID() + "." + ext;
        String relativePath = "floor-plans" + File.separator + branchId + File.separator + subDir + File.separator + filename;
        Path uploadPath = Paths.get(uploadDir, relativePath);
        Files.createDirectories(uploadPath.getParent());
        file.transferTo(uploadPath.toFile());

        return "/api/floor-plans/files/" + relativePath.replace("\\", "/");
    }

    private Map<String, Object> toObjectResponse(FloorPlanObject obj) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", obj.getId());
        response.put("tableId", obj.getTableId());
        response.put("objectType", obj.getObjectType());
        response.put("label", obj.getLabel() != null ? obj.getLabel() : "");
        response.put("x", obj.getX());
        response.put("y", obj.getY());
        response.put("width", obj.getWidth());
        response.put("height", obj.getHeight());
        response.put("rotation", obj.getRotation());
        response.put("shape", obj.getShape() != null ? obj.getShape() : "");
        response.put("zIndex", obj.getZIndex());
        response.put("styleJson", obj.getStyleJson() != null ? obj.getStyleJson() : Map.of());
        response.put("metadataJson", obj.getMetadataJson() != null ? obj.getMetadataJson() : Map.of());
        response.put("isVisible", obj.getIsVisible() == null || obj.getIsVisible());
        response.put("isLocked", obj.getIsLocked() != null && obj.getIsLocked());
        return response;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(value.toString());
    }

    private Map<String, Object> toFloorPlanResponse(FloorPlan plan) {
        return toFloorPlanResponse(plan, false);
    }

    private Map<String, Object> toFloorPlanResponse(FloorPlan plan, boolean includeObjects) {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("id", plan.getId());
        response.put("branch", Map.of("branchId", plan.getBranch().getBranchId()));
        response.put("roomId", plan.getRoom() != null ? plan.getRoom().getId() : null);
        response.put("room", plan.getRoom() != null
                ? Map.of(
                "id", plan.getRoom().getId(),
                "name", plan.getRoom().getName(),
                "displayOrder", plan.getRoom().getDisplayOrder() != null ? plan.getRoom().getDisplayOrder() : 0
        )
                : null);
        response.put("name", plan.getName());
        response.put("floorNumber", plan.getFloorNumber());
        response.put("width", plan.getWidth());
        response.put("height", plan.getHeight());
        response.put("floorDiagramImageUrl", plan.getFloorDiagramImageUrl());
        response.put("floorDiagramImageKey", plan.getFloorDiagramImageKey());
        response.put("floorDiagramFitMode", plan.getFloorDiagramFitMode());
        response.put("floorDiagramX", plan.getFloorDiagramX());
        response.put("floorDiagramY", plan.getFloorDiagramY());
        response.put("floorDiagramWidth", plan.getFloorDiagramWidth());
        response.put("floorDiagramHeight", plan.getFloorDiagramHeight());
        response.put("floorDiagramScale", plan.getFloorDiagramScale());
        response.put("floorDiagramRotation", plan.getFloorDiagramRotation());
        response.put("backgroundMode", plan.getBackgroundMode());
        response.put("panoramaUrl", plan.getPanoramaUrl());
        response.put("panoramaKey", plan.getPanoramaKey());
        response.put("panoramaType", plan.getPanoramaType());
        response.put("status", plan.getStatus());
        response.put("createdAt", plan.getCreatedAt());
        response.put("updatedAt", plan.getUpdatedAt());
        if (includeObjects) {
            response.put("floorPlanObjects", floorPlanService.listObjects(plan.getId()).stream()
                    .map(this::toObjectResponse)
                    .toList());
        }
        return response;
    }

    // ─── Floor Plan CRUD ──────────────────────────────────────────────

    @GetMapping("/api/branches/{branchId}/floor-plans")
    public ResponseEntity<?> listFloorPlans(@PathVariable String branchId) {
        try {
            User user = getLoggedInUser();
            if (user == null) return ResponseEntity.status(401).body("Not authenticated");

            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateAndGetBranchId(branchId, error);
            if (error.hasError()) return error.toResponse();

            List<FloorPlan> plans = floorPlanService.listFloorPlans(branchId);
            return ResponseEntity.ok(plans.stream().map(this::toFloorPlanResponse).toList());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Create Floor Plan

    @PostMapping("/api/branches/{branchId}/floor-plans")
    public ResponseEntity<?> createFloorPlan(@PathVariable String branchId, @RequestBody Map<String, Object> body) {
        try {
            User user = getLoggedInUser();
            if (user == null) return ResponseEntity.status(401).body("Not authenticated");
            requireAdminOrManager(user);

            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateAndGetBranchId(branchId, error);
            if (error.hasError()) return error.toResponse();

            String name = (String) body.getOrDefault("name", "New Floor Plan");
            Long roomId = body.get("roomId") != null ? ((Number) body.get("roomId")).longValue() : null;
            Integer floorNumber = body.get("floorNumber") != null ? ((Number) body.get("floorNumber")).intValue() : 1;
            Integer width = body.get("width") != null ? ((Number) body.get("width")).intValue() : 1200;
            Integer height = body.get("height") != null ? ((Number) body.get("height")).intValue() : 800;
            String backgroundMode = body.get("backgroundMode") != null ? (String) body.get("backgroundMode") : "DEFAULT_WOOD";

            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Tên sơ đồ không được để trống"));
            }

            if (roomId == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Room/area is required"));
            }

            FloorPlan plan = floorPlanService.createFloorPlan(branchId, roomId, name.trim(), floorNumber, width, height, backgroundMode, user);
            return ResponseEntity.ok(toFloorPlanResponse(plan));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/floor-plans")
    public ResponseEntity<?> createFloorPlan(@RequestBody Map<String, Object> body) {
        try {
            User user = getLoggedInUser();
            if (user == null) return ResponseEntity.status(401).body("Not authenticated");
            requireAdminOrManager(user);

            String branchId = body.get("branchId") != null ? body.get("branchId").toString() : null;
            if (branchId == null || branchId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Branch is required"));
            }

            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateAndGetBranchId(branchId, error);
            if (error.hasError()) return error.toResponse();

            String name = (String) body.getOrDefault("name", "New Floor Plan");
            Long roomId = toLong(body.get("roomId"));
            Integer floorNumber = body.get("floorNumber") != null ? ((Number) body.get("floorNumber")).intValue() : null;
            Integer width = body.get("width") != null ? ((Number) body.get("width")).intValue() : 1200;
            Integer height = body.get("height") != null ? ((Number) body.get("height")).intValue() : 800;
            String backgroundMode = body.get("backgroundMode") != null ? (String) body.get("backgroundMode") : "DEFAULT_WOOD";

            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Floor plan name is required"));
            }
            if (roomId == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Room/area is required"));
            }

            FloorPlan plan = floorPlanService.createFloorPlan(branchId, roomId, name.trim(), floorNumber, width, height, backgroundMode, user);
            return ResponseEntity.ok(toFloorPlanResponse(plan));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/api/floor-plans/{id}")
    public ResponseEntity<?> getFloorPlan(@PathVariable Long id) {
        try {
            FloorPlan plan = floorPlanService.getFloorPlan(id);
            return ResponseEntity.ok(toFloorPlanResponse(plan, true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/api/floor-plans/{id}")
    public ResponseEntity<?> updateFloorPlan(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            User user = getLoggedInUser();
            if (user == null) return ResponseEntity.status(401).body("Not authenticated");
            requireAdminOrManager(user);

            FloorPlan plan = floorPlanService.getFloorPlan(id);
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(plan.getBranch().getBranchId(), error);
            if (error.hasError()) return error.toResponse();

            FloorPlan updated = floorPlanService.updateFloorPlan(id, body, user);
            return ResponseEntity.ok(toFloorPlanResponse(updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/api/floor-plans/{id}")
    public ResponseEntity<?> deleteFloorPlan(@PathVariable Long id) {
        try {
            User user = getLoggedInUser();
            if (user == null) return message(401, "Not authenticated");
            requireAdminOrManager(user);

            FloorPlan plan = floorPlanService.getFloorPlan(id);
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(plan.getBranch().getBranchId(), error);
            if (error.hasError()) return error.toResponse();

            floorPlanService.deleteFloorPlan(id);
            return ResponseEntity.ok(Map.of("message", "Đã xóa sơ đồ tầng"));
        } catch (SecurityException e) {
            return message(403, e.getMessage());
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Floor plan not found")) {
                return message(404, "Không tìm thấy sơ đồ tầng");
            }
            return message(400, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete floor plan {}", id, e);
            return message(500, "Xóa sơ đồ tầng thất bại: " + e.getMessage());
        }
    }

    // Publish / Unpublish

    @PostMapping("/api/floor-plans/{id}/publish")
    public ResponseEntity<?> publishFloorPlan(@PathVariable Long id) {
        try {
            User user = getLoggedInUser();
            if (user == null) return ResponseEntity.status(401).body("Not authenticated");
            requireAdminOrManager(user);

            FloorPlan plan = floorPlanService.getFloorPlan(id);
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(plan.getBranch().getBranchId(), error);
            if (error.hasError()) return error.toResponse();

            FloorPlan published = floorPlanService.publishFloorPlan(id, user);
            return ResponseEntity.ok(toFloorPlanResponse(published));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/floor-plans/{id}/unpublish")
    public ResponseEntity<?> unpublishFloorPlan(@PathVariable Long id) {
        try {
            User user = getLoggedInUser();
            if (user == null) return ResponseEntity.status(401).body("Not authenticated");
            requireAdminOrManager(user);

            FloorPlan plan = floorPlanService.getFloorPlan(id);
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(plan.getBranch().getBranchId(), error);
            if (error.hasError()) return error.toResponse();

            FloorPlan unpublished = floorPlanService.unpublishFloorPlan(id, user);
            return ResponseEntity.ok(toFloorPlanResponse(unpublished));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Upload Floor Diagram

    @PostMapping(value = "/api/floor-plans/{id}/upload-diagram", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDiagram(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            User user = getLoggedInUser();
            if (user == null) return ResponseEntity.status(401).body("Not authenticated");
            if (!isAdminOrManager(user)) {
                return ResponseEntity.status(403).body(Map.of("message", "Permission denied"));
            }

            FloorPlan plan = floorPlanService.getFloorPlan(id);
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(plan.getBranch().getBranchId(), error);
            if (error.hasError()) return error.toResponse();

            validateDirectImageUpload(id, "diagram", file);
            String fileKey = uploadService.buildObjectKey("floor-plans", "diagram", id, file.getOriginalFilename());
            String url = uploadService.saveLocalUpload(fileKey, file);
            FloorPlan updated = floorPlanService.updateFloorDiagramImage(id, url, fileKey, user);
            return ResponseEntity.ok(toFloorPlanResponse(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to upload floor diagram for floor plan {}", id, e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Upload failed: " + e.getMessage()));
        }
    }

    // Presign endpoint for floor plan uploads

    @PostMapping("/api/floor-plans/{id}/presign")
    public ResponseEntity<?> presignUpload(@PathVariable Long id, @RequestBody PresignRequest request) {
        try {
            User user = getLoggedInUser();
            if (user == null) return ResponseEntity.status(401).body("Not authenticated");
            requireAdminOrManager(user);

            FloorPlan plan = floorPlanService.getFloorPlan(id);
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(plan.getBranch().getBranchId(), error);
            if (error.hasError()) return error.toResponse();

            request.setFloorPlanId(id);
            PresignResponse response = uploadService.createPresignedUrl(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Presign failed for floor plan {}", id, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload configuration error"));
        }
    }

    // Upload Background

    @PostMapping("/api/floor-plans/{id}/upload-background")
    public ResponseEntity<?> uploadBackground(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            User user = getLoggedInUser();
            if (user == null) return ResponseEntity.status(401).body("Not authenticated");
            requireAdminOrManager(user);

            FloorPlan plan = floorPlanService.getFloorPlan(id);
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(plan.getBranch().getBranchId(), error);
            if (error.hasError()) return error.toResponse();

            String url = saveUploadedFile(file, plan.getBranch().getBranchId(), "backgrounds");
            FloorPlan updated = floorPlanService.updateBackgroundImage(id, url, user);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Upload 360 Panorama

    @PostMapping(value = "/api/floor-plans/{id}/upload-360", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload360(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            User user = getLoggedInUser();
            if (user == null) return ResponseEntity.status(401).body("Not authenticated");
            requireAdminOrManager(user);

            FloorPlan plan = floorPlanService.getFloorPlan(id);
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(plan.getBranch().getBranchId(), error);
            if (error.hasError()) return error.toResponse();

            validateDirectImageUpload(id, "panorama", file);
            String fileKey = uploadService.buildObjectKey("floor-plans", "panorama", id, file.getOriginalFilename());
            String url = uploadService.saveLocalUpload(fileKey, file);
            FloorPlan updated = floorPlanService.updatePanoramaImage(id, url, fileKey, user);
            return ResponseEntity.ok(toFloorPlanResponse(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to upload panorama for floor plan {}", id, e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Upload failed: " + e.getMessage()));
        }
    }

    // Floor Plan Objects CRUD

    @GetMapping("/api/floor-plans/{id}/objects")
    public ResponseEntity<?> listObjects(@PathVariable Long id) {
        try {
            FloorPlan plan = floorPlanService.getFloorPlan(id);
            return ResponseEntity.ok(floorPlanService.listObjects(id).stream()
                    .map(this::toObjectResponse)
                    .toList());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/api/floor-plans/{id}/objects")
    public ResponseEntity<?> createObject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            User user = getLoggedInUser();
            if (user == null) return ResponseEntity.status(401).body("Not authenticated");
            requireAdminOrManager(user);

            FloorPlan plan = floorPlanService.getFloorPlan(id);
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(plan.getBranch().getBranchId(), error);
            if (error.hasError()) return error.toResponse();

            return ResponseEntity.ok(toObjectResponse(floorPlanService.createObject(id, body)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/api/floor-plan-objects/{objectId}")
    public ResponseEntity<?> updateObject(@PathVariable Long objectId, @RequestBody Map<String, Object> body) {
        try {
            User user = getLoggedInUser();
            if (user == null) return ResponseEntity.status(401).body("Not authenticated");
            requireAdminOrManager(user);

            FloorPlanObject obj = floorPlanService.getObject(objectId);
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(obj.getFloorPlan().getBranch().getBranchId(), error);
            if (error.hasError()) return error.toResponse();

            return ResponseEntity.ok(toObjectResponse(floorPlanService.updateObject(objectId, body)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/api/floor-plan-objects/{objectId}")
    public ResponseEntity<?> deleteObject(@PathVariable Long objectId) {
        try {
            User user = getLoggedInUser();
            if (user == null) return ResponseEntity.status(401).body("Not authenticated");
            requireAdminOrManager(user);

            FloorPlanObject obj = floorPlanService.getObject(objectId);
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(obj.getFloorPlan().getBranch().getBranchId(), error);
            if (error.hasError()) return error.toResponse();

            floorPlanService.deleteObject(objectId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/api/floor-plans/{id}/objects/bulk")
    public ResponseEntity<?> bulkSaveObjects(@PathVariable Long id, @RequestBody List<Map<String, Object>> body) {
        try {
            User user = getLoggedInUser();
            if (user == null) return ResponseEntity.status(401).body("Not authenticated");
            requireAdminOrManager(user);

            FloorPlan plan = floorPlanService.getFloorPlan(id);
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(plan.getBranch().getBranchId(), error);
            if (error.hasError()) return error.toResponse();

            List<FloorPlanObject> objects = floorPlanService.bulkSaveObjects(id, body);
            return ResponseEntity.ok(objects.stream()
                    .map(this::toObjectResponse)
                    .toList());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
