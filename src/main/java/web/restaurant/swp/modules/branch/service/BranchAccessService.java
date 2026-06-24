package web.restaurant.swp.modules.branch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import web.restaurant.swp.config.BranchContext;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.UserRepository;
import web.restaurant.swp.modules.branch.model.Branch;
import web.restaurant.swp.modules.branch.repository.BranchRepository;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class BranchAccessService {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;

    public User getLoggedInUser() {
        return BranchContext.getLoggedInUser(userRepository);
    }

    public String getActiveBranchId() {
        User user = getLoggedInUser();
        return BranchContext.getActiveBranchId(user);
    }

    public boolean isAdmin(User user) {
        if (user == null) return false;
        return user.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getName()));
    }

    public boolean canSwitchBranch(User user) {
        return BranchContext.canSwitchBranch(user);
    }

    /**
     * Validate that the current user can access the given branchId.
     * Returns the resolved branchId if authorized.
     * Returns null and sets errorResponse if not authorized.
     *
     * @param requestedBranchId the branchId requested (from param/body/header)
     * @param errorResponse holder for the error response if authorization fails
     * @return the authorized branchId, or null if not authorized
     */
    public String validateAndGetBranchId(String requestedBranchId, ErrorHolder errorResponse) {
        User user = getLoggedInUser();
        if (user == null) {
            errorResponse.set(401, "Not authenticated");
            return null;
        }

        String branchId = (requestedBranchId != null && !requestedBranchId.trim().isEmpty())
                ? requestedBranchId
                : getActiveBranchId();

        if (branchId == null || branchId.trim().isEmpty()) {
            errorResponse.set(400, "Branch context is required");
            return null;
        }

        if (isAdmin(user)) {
            return branchId;
        }

        if (user.getBranch() == null) {
            errorResponse.set(403, "No branch assigned to your account");
            return null;
        }

        if (!user.getBranch().getBranchId().equals(branchId)) {
            errorResponse.set(403, "You do not have access to this branch");
            return null;
        }

        return branchId;
    }

    /**
     * Validate that the current user can access the branch derived from an entity.
     * For POS session-based operations where branch is derived from table->room->branch.
     *
     * @param entityBranchId the branchId derived from the entity
     * @param errorResponse holder for the error response if authorization fails
     * @return the entityBranchId if authorized, or null if not
     */
    public String validateEntityBranch(String entityBranchId, ErrorHolder errorResponse) {
        User user = getLoggedInUser();
        if (user == null) {
            errorResponse.set(401, "Not authenticated");
            return null;
        }

        if (entityBranchId == null || entityBranchId.trim().isEmpty()) {
            errorResponse.set(400, "Branch context is required");
            return null;
        }

        if (isAdmin(user)) {
            return entityBranchId;
        }

        if (user.getBranch() == null) {
            errorResponse.set(403, "No branch assigned to your account");
            return null;
        }

        if (!user.getBranch().getBranchId().equals(entityBranchId)) {
            errorResponse.set(403, "You do not have access to this branch");
            return null;
        }

        return entityBranchId;
    }

    /**
     * Validates that a given branchId is valid and active.
     */
    public Branch validateBranchExists(String branchId, ErrorHolder errorResponse) {
        if (branchId == null || branchId.trim().isEmpty()) {
            errorResponse.set(400, "Branch ID is required");
            return null;
        }

        Branch branch = branchRepository.findById(branchId).orElse(null);
        if (branch == null || !branch.isActive()) {
            errorResponse.set(400, "Branch not found or inactive");
            return null;
        }

        return branch;
    }

    public static class ErrorHolder {
        private int status;
        private String message;

        public void set(int status, String message) {
            this.status = status;
            this.message = message;
        }

        public boolean hasError() {
            return message != null;
        }

        public int getStatus() { return status; }
        public String getMessage() { return message; }

        public ResponseEntity<?> toResponse() {
            return ResponseEntity.status(status).body(Map.of("error", message));
        }
    }
}
