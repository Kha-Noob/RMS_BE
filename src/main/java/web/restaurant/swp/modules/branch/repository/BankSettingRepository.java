package web.restaurant.swp.modules.branch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.restaurant.swp.modules.branch.model.BankSetting;
import java.util.Optional;

@Repository
public interface BankSettingRepository extends JpaRepository<BankSetting, Long> {
    Optional<BankSetting> findByBranchBranchId(String branchId);
}
