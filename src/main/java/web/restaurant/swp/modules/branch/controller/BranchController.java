package web.restaurant.swp.modules.branch.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import web.restaurant.swp.config.BranchContext;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.UserRepository;
import web.restaurant.swp.modules.branch.model.Branch;
import web.restaurant.swp.modules.branch.repository.BranchRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class BranchController {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;

    @GetMapping("/api/branches/my-branches")
    public ResponseEntity<?> getMyBranches() {
        User user = BranchContext.getLoggedInUser(userRepository);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        if (user.getTenant() == null) {
            return ResponseEntity.ok(List.of());
        }

        List<Branch> branches;
        if (BranchContext.canSwitchBranch(user)) {
            branches = branchRepository.findByTenantTenantIdAndIsActiveTrue(
                    user.getTenant().getTenantId());
        } else if (user.getBranch() != null) {
            branches = List.of(user.getBranch());
        } else {
            branches = List.of();
        }

        List<Map<String, Object>> result = branches.stream().map(b -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("branchId", b.getBranchId());
            map.put("name", b.getName());
            map.put("address", b.getAddress());
            map.put("phone", b.getPhone());
            map.put("isActive", b.isActive());
            return map;
        }).toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/branches/select")
    public ResponseEntity<?> selectBranch(@RequestBody Map<String, String> body,
                                          HttpServletRequest request) {
        User user = BranchContext.getLoggedInUser(userRepository);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        String branchId = body.get("branchId");
        if (branchId == null || branchId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "branchId is required"));
        }

        Branch branch = branchRepository.findById(branchId).orElse(null);
        if (branch == null || !branch.isActive()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Branch not found or inactive"));
        }

        if (user.getTenant() == null || !branch.getTenant().getTenantId()
                .equals(user.getTenant().getTenantId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Branch not in your tenant"));
        }

        if (!BranchContext.canSwitchBranch(user)) {
            if (user.getBranch() == null || !user.getBranch().getBranchId().equals(branchId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Cannot switch to this branch"));
            }
        }

        HttpSession session = request.getSession(true);
        session.setAttribute("activeBranchId", branchId);

        return ResponseEntity.ok(Map.of("message", "Branch selected", "branchId", branchId));
    }

    @PostMapping("/api/branch/select")
    public ResponseEntity<?> selectBranchLegacy(@RequestParam String branchId,
                                                HttpServletRequest request) {
        return selectBranch(Map.of("branchId", branchId), request);
    }
}
