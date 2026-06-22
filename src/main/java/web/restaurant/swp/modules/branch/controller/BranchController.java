package web.restaurant.swp.modules.branch.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import web.restaurant.swp.config.BranchContext;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.UserRepository;
import web.restaurant.swp.modules.branch.repository.BranchRepository;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class BranchController {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;

    @PostMapping("/api/branch/select")
    public ResponseEntity<?> selectBranch(@RequestParam String branchId, HttpServletRequest request) {
        User loggedInUser = BranchContext.getLoggedInUser(userRepository);
        if (loggedInUser == null) {
            return ResponseEntity.status(401).body("Chưa đăng nhập");
        }

        if (!BranchContext.canSwitchBranch(loggedInUser)) {
            return ResponseEntity.status(403).body("Không có quyền chọn chi nhánh");
        }

        if (!branchRepository.existsById(branchId)) {
            return ResponseEntity.badRequest().body("Chi nhánh không tồn tại");
        }

        HttpSession session = request.getSession(true);
        session.setAttribute("activeBranchId", branchId);

        return ResponseEntity.ok(Map.of("message", "Đã chọn chi nhánh thành công", "branchId", branchId));
    }
}
