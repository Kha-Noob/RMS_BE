package web.restaurant.swp.modules.auth.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.UserRepository;
import web.restaurant.swp.modules.auth.service.S3Service;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final UserRepository userRepository;
    private final S3Service s3Service;

    @PostMapping("/upload-avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("avatar") MultipartFile file) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(401).body(Map.of("message", "Chưa đăng nhập hệ thống."));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File ảnh không được để trống."));
        }

        // Limit size to 5MB
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("message", "Kích thước ảnh không được vượt quá 5MB."));
        }

        try {
            User user = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng."));

            String avatarUrl = s3Service.uploadAvatar(file);
            user.setAvatarUrl(avatarUrl);
            userRepository.save(user);

            log.info("Successfully updated avatar url for user ID {}: {}", user.getId(), avatarUrl);
            return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
        } catch (Exception e) {
            log.error("Failed to upload avatar", e);
            return ResponseEntity.status(500).body(Map.of("message", "Lỗi tải ảnh lên: " + e.getMessage()));
        }
    }

    @PostMapping("/update-info")
    public ResponseEntity<?> updateInfo(@RequestBody Map<String, String> payload) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(401).body(Map.of("message", "Chưa đăng nhập hệ thống."));
        }

        try {
            User user = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng."));

            if (payload.containsKey("name")) {
                user.setName(payload.get("name"));
            }
            if (payload.containsKey("phone")) {
                user.setPhone(payload.get("phone"));
            }
            if (payload.containsKey("birthday")) {
                user.setBirthday(payload.get("birthday"));
            }
            if (payload.containsKey("gender")) {
                user.setGender(payload.get("gender"));
            }
            if (payload.containsKey("dietaryNotes")) {
                user.setDietaryNotes(payload.get("dietaryNotes"));
            }

            userRepository.save(user);
            log.info("Successfully updated profile info for user ID {}", user.getId());

            return ResponseEntity.ok(Map.of("message", "Cập nhật thông tin cá nhân thành công."));
        } catch (Exception e) {
            log.error("Failed to update profile info", e);
            return ResponseEntity.status(500).body(Map.of("message", "Cập nhật thông tin thất bại: " + e.getMessage()));
        }
    }
}
