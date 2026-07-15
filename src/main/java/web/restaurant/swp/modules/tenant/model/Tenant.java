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

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Builder.Default
    @Column(name = "is_using_system_web", nullable = false)
    private boolean isUsingSystemWeb = false;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account_no")
    private String bankAccountNo;

    @Column(name = "bank_account_name")
    private String bankAccountName;

    @Column(name = "bank_branch")
    private String bankBranch;

    @PrePersist
    public void ensureId() {
        if (this.tenantId == null) {
            this.tenantId = UUID.randomUUID().toString();
        }
    }
}
