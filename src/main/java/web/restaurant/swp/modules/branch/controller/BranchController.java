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
import java.util.Optional;

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

    @GetMapping("/api/branches/all")
    public ResponseEntity<?> getAllBranches() {
        User user = BranchContext.getLoggedInUser(userRepository);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        if (user.getTenant() == null) {
            return ResponseEntity.ok(List.of());
        }

        // Return all branches (including inactive ones) of this tenant
        List<Branch> branches = branchRepository.findAll().stream()
                .filter(b -> b.getTenant() != null && b.getTenant().getTenantId().equals(user.getTenant().getTenantId()))
                .toList();

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

    @PostMapping("/api/branches")
    public ResponseEntity<?> createBranch(@RequestBody Map<String, String> payload) {
        User user = BranchContext.getLoggedInUser(userRepository);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        boolean hasAccess = user.getRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "COOPERATOR".equalsIgnoreCase(r.getName()));
        if (!hasAccess) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        String branchId = payload.get("branchId");
        String name = payload.get("name");
        String address = payload.get("address");
        String phone = payload.get("phone");

        if (branchId == null || branchId.trim().isEmpty() ||
            name == null || name.trim().isEmpty() ||
            address == null || address.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vui lòng nhập đầy đủ mã, tên và địa chỉ chi nhánh."));
        }

        Optional<Branch> existing = branchRepository.findById(branchId.trim());
        if (existing.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mã chi nhánh đã tồn tại trong hệ thống. Vui lòng chọn mã khác."));
        }

        Branch branch = Branch.builder()
                .branchId(branchId.trim())
                .name(name.trim())
                .address(address.trim())
                .phone(phone != null ? phone.trim() : "")
                .tenant(user.getTenant())
                .isActive(true)
                .build();

        branch = branchRepository.save(branch);
        return ResponseEntity.ok(branch);
    }

    @PutMapping("/api/branches/{id}")
    public ResponseEntity<?> updateBranch(@PathVariable String id, @RequestBody Map<String, Object> payload) {
        User user = BranchContext.getLoggedInUser(userRepository);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        boolean hasAccess = user.getRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "COOPERATOR".equalsIgnoreCase(r.getName()));
        if (!hasAccess) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        Branch branch = branchRepository.findById(id).orElse(null);
        if (branch == null) {
            return ResponseEntity.notFound().build();
        }

        // Verify tenant matches
        if (user.getTenant() == null || branch.getTenant() == null || !branch.getTenant().getTenantId().equals(user.getTenant().getTenantId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Chi nhánh không thuộc quyền quản lý của bạn."));
        }

        if (payload.containsKey("name")) {
            branch.setName(((String) payload.get("name")).trim());
        }
        if (payload.containsKey("address")) {
            branch.setAddress(((String) payload.get("address")).trim());
        }
        if (payload.containsKey("phone")) {
            branch.setPhone(((String) payload.get("phone")).trim());
        }
        if (payload.containsKey("isActive")) {
            branch.setActive((Boolean) payload.get("isActive"));
        }

        branch = branchRepository.save(branch);
        return ResponseEntity.ok(branch);
    }

    @DeleteMapping("/api/branches/{id}")
    public ResponseEntity<?> toggleBranchStatus(@PathVariable String id) {
        User user = BranchContext.getLoggedInUser(userRepository);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        boolean hasAccess = user.getRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "COOPERATOR".equalsIgnoreCase(r.getName()));
        if (!hasAccess) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        Branch branch = branchRepository.findById(id).orElse(null);
        if (branch == null) {
            return ResponseEntity.notFound().build();
        }

        if (user.getTenant() == null || branch.getTenant() == null || !branch.getTenant().getTenantId().equals(user.getTenant().getTenantId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Chi nhánh không thuộc quyền quản lý của bạn."));
        }

        branch.setActive(!branch.isActive());
        branch = branchRepository.save(branch);
        return ResponseEntity.ok(branch);
    }
}
