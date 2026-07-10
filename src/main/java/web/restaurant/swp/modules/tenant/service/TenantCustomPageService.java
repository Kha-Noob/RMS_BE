package web.restaurant.swp.modules.tenant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.tenant.model.Tenant;
import web.restaurant.swp.modules.tenant.model.TenantCustomPage;
import web.restaurant.swp.modules.tenant.repository.TenantRepository;
import web.restaurant.swp.modules.tenant.repository.TenantCustomPageRepository;
import web.restaurant.swp.modules.review.service.AIContentService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantCustomPageService {

    private final TenantCustomPageRepository tenantCustomPageRepository;
    private final TenantRepository tenantRepository;
    private final AIContentService aiContentService;

    public Optional<TenantCustomPage> getCustomPageByTenantId(String tenantId) {
        return tenantCustomPageRepository.findByTenantId(tenantId);
    }

    public List<TenantCustomPage> getAllPublishedCustomPages() {
        return tenantCustomPageRepository.findByIsPublishedTrue();
    }

    public Map<String, Object> generateThemeFromPrompt(String prompt) {
        return generateThemeFromPrompt(prompt, null);
    }

    public Map<String, Object> generateThemeFromPrompt(String prompt, Map<String, Object> currentSettings) {
        return aiContentService.generateUiThemeConfig(prompt, currentSettings);
    }

    @Transactional
    public TenantCustomPage saveCustomPage(String tenantId, TenantCustomPage pageSettings, User user) {
        // Validate tenant
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant không tồn tại."));

        // If user is not superadmin, verify they belong to this tenant
        boolean isAdmin = user.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN"));
        boolean isCooperator = user.getRoles().stream().anyMatch(r -> r.getName().equals("COOPERATOR"));
        
        if (!isAdmin && isCooperator) {
            if (user.getTenant() == null || !user.getTenant().getTenantId().equals(tenantId)) {
                throw new RuntimeException("Bạn không có quyền chỉnh sửa trang của Tenant này.");
            }
        }

        TenantCustomPage existingPage = tenantCustomPageRepository.findByTenantId(tenantId).orElse(null);
        if (existingPage == null) {
            pageSettings.setTenantId(tenantId);
            pageSettings.setRestaurantName(tenant.getName());
            pageSettings.setCreatedAt(LocalDateTime.now());
            pageSettings.setUpdatedAt(LocalDateTime.now());
            return tenantCustomPageRepository.save(pageSettings);
        } else {
            existingPage.setRestaurantName(tenant.getName());
            existingPage.setDescription(pageSettings.getDescription());
            existingPage.setStylePrompt(pageSettings.getStylePrompt());
            existingPage.setPrimaryColor(pageSettings.getPrimaryColor());
            existingPage.setSecondaryColor(pageSettings.getSecondaryColor());
            existingPage.setBackgroundColor(pageSettings.getBackgroundColor());
            existingPage.setTextColor(pageSettings.getTextColor());
            existingPage.setFontFamily(pageSettings.getFontFamily());
            existingPage.setLayoutStyle(pageSettings.getLayoutStyle());
            existingPage.setCoverImageUrl(pageSettings.getCoverImageUrl());
            existingPage.setShowPosts(pageSettings.getShowPosts() != null ? pageSettings.getShowPosts() : existingPage.getShowPosts());
            existingPage.setShowEvents(pageSettings.getShowEvents() != null ? pageSettings.getShowEvents() : existingPage.getShowEvents());
            existingPage.setShowReviews(pageSettings.getShowReviews() != null ? pageSettings.getShowReviews() : existingPage.getShowReviews());
            existingPage.setIsPublished(pageSettings.getIsPublished() != null ? pageSettings.getIsPublished() : existingPage.getIsPublished());
            existingPage.setUpdatedAt(LocalDateTime.now());
            return tenantCustomPageRepository.save(existingPage);
        }
    }
}
