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
        // System admin has branch == null, and the specific user '2thang9@liteflow.com' is allowed to switch branches
        return user.getBranch() == null || "2thang9@liteflow.com".equals(user.getEmail());
    }

    public static String getActiveBranchId(User user) {
        if (user == null) {
            return "01-2thang9";
        }

        if (canSwitchBranch(user)) {
            try {
                ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
                HttpServletRequest request = attr.getRequest();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    String sessionBranchId = (String) session.getAttribute("activeBranchId");
                    if (sessionBranchId != null) {
                        return sessionBranchId;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (user.getBranch() != null) {
            return user.getBranch().getBranchId();
        }

        return "01-2thang9";
    }
}
