package web.restaurant.swp.modules.floorplan.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequiredArgsConstructor
@Slf4j
public class FloorPlanManagementController {

    private final FloorPlanService floorPlanService;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;

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
            throw new RuntimeException("Không có quyền thực hiện thao tác này");
        }
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
            return ResponseEntity.ok(plans);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

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
            Integer floorNumber = body.get("floorNumber") != null ? ((Number) body.get("floorNumber")).intValue() : 1;
            Integer width = body.get("width") != null ? ((Number) body.get("width")).intValue() : 1200;
            Integer height = body.get("height") != null ? ((Number) body.get("height")).intValue() : 800;

            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Tên floor plan không được để trống");
            }

            FloorPlan plan = floorPlanService.createFloorPlan(branchId, name.trim(), floorNumber, width, height, user);
            return ResponseEntity.ok(plan);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/floor-plans/{id}")
    public ResponseEntity<?> getFloorPlan(@PathVariable Long id) {
        try {
            FloorPlan plan = floorPlanService.getFloorPlanWithObjects(id);
            return ResponseEntity.ok(plan);
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
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/api/floor-plans/{id}")
    public ResponseEntity<?> deleteFloorPlan(@PathVariable Long id) {
        try {
            User user = getLoggedInUser();
            if (user == null) return ResponseEntity.status(401).body("Not authenticated");
            requireAdminOrManager(user);

            FloorPlan plan = floorPlanService.getFloorPlan(id);
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(plan.getBranch().getBranchId(), error);
            if (error.hasError()) return error.toResponse();

            floorPlanService.deleteFloorPlan(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

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
            return ResponseEntity.ok(published);
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
            return ResponseEntity.ok(unpublished);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ─── File Upload ──────────────────────────────────────────────────

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

    @PostMapping("/api/floor-plans/{id}/upload-360")
    public ResponseEntity<?> upload360(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            User user = getLoggedInUser();
            if (user == null) return ResponseEntity.status(401).body("Not authenticated");
            requireAdminOrManager(user);

            FloorPlan plan = floorPlanService.getFloorPlan(id);
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(plan.getBranch().getBranchId(), error);
            if (error.hasError()) return error.toResponse();

            String url = saveUploadedFile(file, plan.getBranch().getBranchId(), "panoramas");
            FloorPlan updated = floorPlanService.update360Image(id, url, user);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
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

    // ─── Floor Plan Objects CRUD ──────────────────────────────────────

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

            String styleJson = body.get("styleJson") != null ? body.get("styleJson").toString() : null;
            String metadataJson = body.get("metadataJson") != null ? body.get("metadataJson").toString() : null;

            FloorPlanObject obj = floorPlanService.createObject(
                    id,
                    (String) body.get("objectType"),
                    (String) body.get("label"),
                    body.get("x") != null ? ((Number) body.get("x")).doubleValue() : null,
                    body.get("y") != null ? ((Number) body.get("y")).doubleValue() : null,
                    body.get("width") != null ? ((Number) body.get("width")).doubleValue() : null,
                    body.get("height") != null ? ((Number) body.get("height")).doubleValue() : null,
                    body.get("rotation") != null ? ((Number) body.get("rotation")).doubleValue() : null,
                    (String) body.get("shape"),
                    body.get("zIndex") != null ? ((Number) body.get("zIndex")).intValue() : null,
                    styleJson,
                    metadataJson
            );
            return ResponseEntity.ok(obj);
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
            FloorPlan plan = obj.getFloorPlan();
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(plan.getBranch().getBranchId(), error);
            if (error.hasError()) return error.toResponse();

            FloorPlanObject updated = floorPlanService.updateObject(objectId, body);
            return ResponseEntity.ok(updated);
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
            FloorPlan plan = obj.getFloorPlan();
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(plan.getBranch().getBranchId(), error);
            if (error.hasError()) return error.toResponse();

            floorPlanService.deleteObject(objectId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/api/floor-plans/{id}/objects/bulk")
    public ResponseEntity<?> bulkUpdateObjects(@PathVariable Long id, @RequestBody List<Map<String, Object>> body) {
        try {
            User user = getLoggedInUser();
            if (user == null) return ResponseEntity.status(401).body("Not authenticated");
            requireAdminOrManager(user);

            FloorPlan plan = floorPlanService.getFloorPlan(id);
            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(plan.getBranch().getBranchId(), error);
            if (error.hasError()) return error.toResponse();

            List<FloorPlanObject> objects = floorPlanService.bulkUpdateObjects(id, body);
            return ResponseEntity.ok(objects);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
