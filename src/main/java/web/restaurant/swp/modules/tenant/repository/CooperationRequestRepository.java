package web.restaurant.swp.modules.tenant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.restaurant.swp.modules.tenant.model.CooperationRequest;

import java.util.List;

@Repository
public interface CooperationRequestRepository extends JpaRepository<CooperationRequest, Long> {
    List<CooperationRequest> findByUserEmailOrderByCreatedAtDesc(String email);
    List<CooperationRequest> findAllByOrderByCreatedAtDesc();
    java.util.Optional<CooperationRequest> findByOrderCode(Long orderCode);
}
