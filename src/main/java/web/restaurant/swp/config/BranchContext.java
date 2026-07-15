package web.restaurant.swp.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.UserRepository;

public class BranchContext {

    public static User getLoggedInUser(UserRepository userRepository) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    public static boolean canSwitchBranch(User user) {
        if (user == null) return false;
        return user.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getName()) || "COOPERATOR".equals(r.getName()));
    }

    public static String getActiveBranchId(User user) {
        if (user == null) {
            return null;
        }

        try {
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attr.getRequest();

            // 1. X-Branch-Id header (highest priority)
            String headerBranchId = request.getHeader("X-Branch-Id");
            if (headerBranchId != null && !headerBranchId.trim().isEmpty()) {
                return headerBranchId;
            }

            // 2. HTTP session (for admin users who switched via session)
            if (canSwitchBranch(user)) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    String sessionBranchId = (String) session.getAttribute("activeBranchId");
                    if (sessionBranchId != null) {
                        return sessionBranchId;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 3. User's assigned branch
        if (user.getBranch() != null) {
            return user.getBranch().getBranchId();
        }

        return null;
    }
}
