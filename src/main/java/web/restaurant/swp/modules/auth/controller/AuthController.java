package web.restaurant.swp.modules.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.UserRepository;
import web.restaurant.swp.modules.auth.service.AuthService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        return userRepository.findByEmail(auth.getName())
                .map(user -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("id", user.getId());
                    response.put("email", user.getEmail());
                    response.put("name", user.getName());
                    response.put("roles", user.getRoles().stream().map(r -> r.getName()).toList());
                    response.put("isActive", user.isActive());
                    response.put("branchId", user.getBranch() != null ? user.getBranch().getBranchId() : null);
                    response.put("tenantId", user.getTenant() != null ? user.getTenant().getTenantId() : null);
                    response.put("isUsingSystemWeb", user.getTenant() != null && user.getTenant().isUsingSystemWeb());
                    response.put("avatarUrl", user.getAvatarUrl());
                    response.put("phone", user.getPhone());
                    response.put("birthday", user.getBirthday());
                    response.put("gender", user.getGender());
                    response.put("dietaryNotes", user.getDietaryNotes());
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.status(401).body(Map.of("error", "User not found")));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {
        try {
            String email = payload.get("email");
            String password = payload.get("password");
            authService.login(email, password);
            return ResponseEntity.ok(Map.of("message", "Đăng nhập thành công."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/forgot-password/request")
    public ResponseEntity<?> requestReset(@RequestBody Map<String, String> payload) {
        try {
            String email = payload.get("email");
            authService.sendForgotPasswordOtp(email);
            return ResponseEntity.ok(Map.of("message", "Mã OTP đã được gửi về email của bạn."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/forgot-password/verify")
    public ResponseEntity<?> verifyResetOtp(@RequestBody Map<String, String> payload) {
        try {
            String email = payload.get("email");
            String otp = payload.get("otp");
            boolean isValid = authService.verifyForgotPasswordOtp(email, otp);
            if (isValid) {
                return ResponseEntity.ok(Map.of("message", "Mã OTP hợp lệ."));
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Mã OTP không đúng hoặc đã hết hạn."));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> payload) {
        try {
            String email = payload.get("email");
            String otp = payload.get("otp");
            String newPassword = payload.get("newPassword");
            authService.resetPasswordWithOtp(email, otp, newPassword);
            return ResponseEntity.ok(Map.of("message", "Đặt lại mật khẩu thành công."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> payload) {
        try {
            String oldPassword = payload.get("oldPassword");
            String newPassword = payload.get("newPassword");
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();
            authService.changePassword(email, oldPassword, newPassword);
            return ResponseEntity.ok(Map.of("message", "Password changed successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/oauth2/success")
    public ResponseEntity<?> oauth2Success() {
        return ResponseEntity.ok(Map.of("message", "Đăng nhập Google thành công."));
    }

    @GetMapping("/oauth2/failure")
    public ResponseEntity<?> oauth2Failure(@RequestParam(required = false) String error) {
        return ResponseEntity.badRequest().body(Map.of("error", error != null ? error : "Đăng nhập Google thất bại."));
    }
}
