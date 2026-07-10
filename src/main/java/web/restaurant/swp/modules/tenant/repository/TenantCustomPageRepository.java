package web.restaurant.swp.modules.tenant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.restaurant.swp.modules.tenant.model.TenantCustomPage;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantCustomPageRepository extends JpaRepository<TenantCustomPage, Long> {
    Optional<TenantCustomPage> findByTenantId(String tenantId);
    List<TenantCustomPage> findByIsPublishedTrue();
}
