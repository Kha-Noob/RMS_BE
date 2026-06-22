package web.restaurant.swp.modules.tenant.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {
    @Id
    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    @Column(nullable = false, unique = true)
    private String name;

    private String domain;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @PrePersist
    public void ensureId() {
        if (this.tenantId == null) {
            this.tenantId = UUID.randomUUID().toString();
        }
    }
}
