package web.restaurant.swp.modules.branch.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "bank_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankSetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "bank_code", nullable = false)
    private String bankCode;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Column(name = "account_holder", nullable = false)
    private String accountHolder;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id")
    private Branch branch;
}
