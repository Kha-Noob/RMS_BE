package web.restaurant.swp.modules.upload.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.UserRepository;
import web.restaurant.swp.modules.upload.model.PresignRequest;
import web.restaurant.swp.modules.upload.model.PresignResponse;
import web.restaurant.swp.modules.upload.service.LocalUploadService;
import web.restaurant.swp.modules.upload.service.UploadService;

import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
@Slf4j
public class UploadController {

    private final UploadService uploadService;
    private final LocalUploadService localUploadService;
    private final UserRepository userRepository;

    @PostMapping("/presign")
    public ResponseEntity<?> presign(@RequestBody PresignRequest request) {
        try {
            User user = getLoggedInUser();
            if (user == null) return ResponseEntity.status(401).body("Not authenticated");
            requireAdminOrManager(user);

            PresignResponse response = uploadService.createPresignedUrl(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Presign failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload configuration error"));
        }
    }

    @PostMapping("/local/{token}")
    public ResponseEntity<?> uploadLocal(@PathVariable String token, @RequestParam("file") MultipartFile file) {
        try {
            User user = getLoggedInUser();
            if (user == null) return ResponseEntity.status(401).body("Not authenticated");
            requireAdminOrManager(user);

            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }

            // Decode the Base64url token to get the fileKey from the presign step
            String fileKey = localUploadService.decodeToken(token);
            log.info("Local upload: token decoded to fileKey={}", fileKey);

            String publicUrl = uploadService.saveLocalUpload(fileKey, file);
            log.info("Local upload complete: publicUrl={}", publicUrl);

            return ResponseEntity.ok(Map.of("publicUrl", publicUrl, "fileKey", fileKey));
        } catch (IllegalArgumentException e) {
            log.error("Local upload failed - invalid token", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid upload token: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Local upload failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload failed: " + e.getMessage()));
        }
    }

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
}
