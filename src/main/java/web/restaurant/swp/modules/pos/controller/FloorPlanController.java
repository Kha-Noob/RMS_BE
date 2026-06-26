package web.restaurant.swp.modules.pos.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.UserRepository;
import web.restaurant.swp.modules.auth.service.AuthService;
import web.restaurant.swp.modules.branch.service.BranchAccessService;
import web.restaurant.swp.modules.pos.model.Room;
import web.restaurant.swp.modules.pos.model.TableEntity;
import web.restaurant.swp.modules.pos.repository.RoomRepository;
import web.restaurant.swp.modules.pos.repository.TableRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequiredArgsConstructor
@Slf4j
public class FloorPlanController {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final TableRepository tableRepository;
    private final BranchAccessService branchAccessService;
    private final AuthService authService;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    private User getLoggedInUser() {
        return branchAccessService.getLoggedInUser();
    }

    @PostMapping("/api/floor-plans/upload")
    public ResponseEntity<?> uploadFloorImage(
            @RequestParam("file") MultipartFile file) {
        try {
            User loggedInUser = getLoggedInUser();
            if (loggedInUser == null || loggedInUser.getRoles().stream()
                    .noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "MANAGER".equalsIgnoreCase(r.getName()))) {
                return ResponseEntity.status(403).body("Không có quyền thực hiện thao tác này.");
            }

            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            String branchId = branchAccessService.validateAndGetBranchId(null, error);
            if (error.hasError()) return error.toResponse();

            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File không được để trống");
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                return ResponseEntity.badRequest().body("Tên file không hợp lệ");
            }

            String ext = "";
            int dotIdx = originalFilename.lastIndexOf('.');
            if (dotIdx >= 0) {
                ext = originalFilename.substring(dotIdx + 1).toLowerCase();
            }

            Set<String> allowedTypes = Set.of("jpg", "jpeg", "png", "webp", "gif");
            if (!allowedTypes.contains(ext)) {
                return ResponseEntity.badRequest().body("Chỉ chấp nhận file ảnh: jpg, jpeg, png, webp, gif");
            }

            if (file.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity.badRequest().body("File tối đa 5MB");
            }

            String filename = UUID.randomUUID() + "." + ext;
            String relativePath = "floor-plans" + File.separator + branchId + File.separator + filename;
            Path uploadPath = Paths.get(uploadDir, relativePath);

            Files.createDirectories(uploadPath.getParent());
            file.transferTo(uploadPath.toFile());

            String url = "/api/floor-plans/files/" + relativePath.replace("\\", "/");

            log.info("Floor plan uploaded: {} -> {}", originalFilename, url);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IOException e) {
            log.error("File upload failed", e);
            return ResponseEntity.badRequest().body("Upload file thất bại: " + e.getMessage());
        }
    }

    @PostMapping("/api/floor-plans/rooms/update")
    public ResponseEntity<?> updateRoomFloorPlan(
            @RequestParam Long roomId,
            @RequestParam(required = false) String floorPlanImageUrl,
            @RequestParam(required = false) String floorPlanWidth,
            @RequestParam(required = false) String floorPlanHeight,
            @RequestParam(required = false) String panoramaUrl,
            @RequestParam(required = false) String panoramaType) {
        try {
            User loggedInUser = getLoggedInUser();
            if (loggedInUser == null || loggedInUser.getRoles().stream()
                    .noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "MANAGER".equalsIgnoreCase(r.getName()))) {
                return ResponseEntity.status(403).body("Không có quyền thực hiện thao tác này.");
            }

            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng/khu vực"));

            BranchAccessService.ErrorHolder branchError = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(room.getBranch().getBranchId(), branchError);
            if (branchError.hasError()) return branchError.toResponse();

            if (floorPlanImageUrl != null) room.setFloorPlanImageUrl(floorPlanImageUrl);
            if (floorPlanWidth != null && !floorPlanWidth.isEmpty()) {
                room.setFloorPlanWidth(Integer.parseInt(floorPlanWidth));
            }
            if (floorPlanHeight != null && !floorPlanHeight.isEmpty()) {
                room.setFloorPlanHeight(Integer.parseInt(floorPlanHeight));
            }
            if (panoramaUrl != null) room.setPanoramaUrl(panoramaUrl);
            if (panoramaType != null) room.setPanoramaType(panoramaType);

            room = roomRepository.save(room);
            return ResponseEntity.ok(room);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/floor-plans/rooms/{roomId}/tables")
    public ResponseEntity<?> getRoomTables(@PathVariable Long roomId) {
        try {
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng/khu vực"));

            BranchAccessService.ErrorHolder error = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(room.getBranch().getBranchId(), error);
            if (error.hasError()) return error.toResponse();

            List<TableEntity> tables = tableRepository.findByRoomId(roomId);
            return ResponseEntity.ok(tables);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/api/floor-plans/rooms/{roomId}/tables/{tableId}/layout")
    public ResponseEntity<?> updateTableLayout(
            @PathVariable Long roomId,
            @PathVariable Long tableId,
            @RequestParam Double layoutX,
            @RequestParam Double layoutY,
            @RequestParam(required = false) Double layoutRadius,
            @RequestParam(required = false) Double layoutRotation,
            @RequestParam(required = false) String displayLabel) {
        try {
            User loggedInUser = getLoggedInUser();
            if (loggedInUser == null || loggedInUser.getRoles().stream()
                    .noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "MANAGER".equalsIgnoreCase(r.getName()))) {
                return ResponseEntity.status(403).body("Không có quyền thực hiện thao tác này.");
            }

            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng/khu vực"));

            BranchAccessService.ErrorHolder branchError = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(room.getBranch().getBranchId(), branchError);
            if (branchError.hasError()) return branchError.toResponse();

            TableEntity table = tableRepository.findById(tableId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy bàn"));

            if (!table.getRoom().getId().equals(roomId)) {
                return ResponseEntity.badRequest().body("Bàn không thuộc phòng này");
            }

            table.setLayoutX(layoutX);
            table.setLayoutY(layoutY);
            if (layoutRadius != null) table.setLayoutRadius(layoutRadius);
            if (layoutRotation != null) table.setLayoutRotation(layoutRotation);
            if (displayLabel != null) table.setDisplayLabel(displayLabel);

            table = tableRepository.save(table);
            return ResponseEntity.ok(table);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/api/floor-plans/rooms/{roomId}/tables/layout/bulk")
    public ResponseEntity<?> bulkUpdateTableLayout(
            @PathVariable Long roomId,
            @RequestBody List<Map<String, Object>> layouts) {
        try {
            User loggedInUser = getLoggedInUser();
            if (loggedInUser == null || loggedInUser.getRoles().stream()
                    .noneMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "MANAGER".equalsIgnoreCase(r.getName()))) {
                return ResponseEntity.status(403).body("Không có quyền thực hiện thao tác này.");
            }

            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng/khu vực"));

            BranchAccessService.ErrorHolder branchError = new BranchAccessService.ErrorHolder();
            branchAccessService.validateEntityBranch(room.getBranch().getBranchId(), branchError);
            if (branchError.hasError()) return branchError.toResponse();

            List<TableEntity> updated = new ArrayList<>();
            for (Map<String, Object> layout : layouts) {
                Long tableId = ((Number) layout.get("tableId")).longValue();
                TableEntity table = tableRepository.findById(tableId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy bàn #" + tableId));

                if (!table.getRoom().getId().equals(roomId)) {
                    continue;
                }

                if (layout.containsKey("layoutX")) table.setLayoutX(((Number) layout.get("layoutX")).doubleValue());
                if (layout.containsKey("layoutY")) table.setLayoutY(((Number) layout.get("layoutY")).doubleValue());
                if (layout.containsKey("layoutRadius")) table.setLayoutRadius(((Number) layout.get("layoutRadius")).doubleValue());
                if (layout.containsKey("layoutRotation")) table.setLayoutRotation(((Number) layout.get("layoutRotation")).doubleValue());
                if (layout.containsKey("displayLabel")) table.setDisplayLabel((String) layout.get("displayLabel"));
                if (layout.containsKey("capacity")) table.setCapacity(((Number) layout.get("capacity")).intValue());

                updated.add(tableRepository.save(table));
            }

            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
