package web.restaurant.swp.modules.tenant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.restaurant.swp.modules.tenant.model.Tenant;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, String> {
}
