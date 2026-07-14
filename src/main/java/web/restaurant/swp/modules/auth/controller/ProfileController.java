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
                String name = payload.get("name");
                if (name == null || name.trim().isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Họ và tên không được để trống."));
                }
                if (!name.trim().matches("^[\\p{L}\\s']{2,100}$")) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Họ và tên không hợp lệ (chỉ chứa chữ cái, khoảng trắng, từ 2-100 ký tự)."));
                }
                user.setName(name.trim());
            }
            if (payload.containsKey("phone")) {
                String phone = payload.get("phone");
                if (phone == null || phone.trim().isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Số điện thoại không được để trống."));
                }
                if (!phone.trim().matches("^(0|\\+84)[35789][0-9]{8}$")) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Số điện thoại không đúng định dạng (phải là số điện thoại Việt Nam hợp lệ)."));
                }
                user.setPhone(phone.trim());
            }
            if (payload.containsKey("birthday")) {
                String birthday = payload.get("birthday");
                if (birthday != null && !birthday.trim().isEmpty()) {
                    try {
                        java.time.LocalDate bdate = java.time.LocalDate.parse(birthday.trim());
                        if (bdate.isAfter(java.time.LocalDate.now())) {
                            return ResponseEntity.badRequest().body(Map.of("message", "Ngày sinh phải ở trong quá khứ."));
                        }
                    } catch (Exception e) {
                        return ResponseEntity.badRequest().body(Map.of("message", "Ngày sinh không đúng định dạng YYYY-MM-DD."));
                    }
                    user.setBirthday(birthday.trim());
                } else {
                    user.setBirthday(null);
                }
            }
            if (payload.containsKey("gender")) {
                String gender = payload.get("gender");
                if (gender != null && !gender.trim().isEmpty() && !java.util.List.of("MALE", "FEMALE", "OTHER").contains(gender.trim().toUpperCase())) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Giới tính không hợp lệ."));
                }
                user.setGender(gender != null ? gender.trim() : null);
            }
            if (payload.containsKey("dietaryNotes")) {
                String dNotes = payload.get("dietaryNotes");
                if (dNotes != null && dNotes.length() > 500) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Ghi chú ăn kiêng không được vượt quá 500 ký tự."));
                }
                user.setDietaryNotes(dNotes != null ? dNotes.trim() : null);
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
