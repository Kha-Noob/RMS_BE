package web.restaurant.swp.modules.floorplan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.restaurant.swp.modules.floorplan.model.FloorPlan;

import java.util.List;

@Repository
public interface FloorPlanRepository extends JpaRepository<FloorPlan, Long> {
    List<FloorPlan> findByBranch_BranchIdOrderByFloorNumberAsc(String branchId);
    List<FloorPlan> findByBranch_BranchIdAndStatusOrderByFloorNumberAsc(String branchId, String status);
    List<FloorPlan> findByBranch_BranchIdOrderByRoom_DisplayOrderAscNameAsc(String branchId);
    List<FloorPlan> findByBranch_BranchIdAndStatusOrderByRoom_DisplayOrderAscNameAsc(String branchId, String status);
    List<FloorPlan> findByRoom_IdOrderByIdAsc(Long roomId);
    List<FloorPlan> findByRoom_IdAndStatusOrderByIdAsc(Long roomId, String status);
    boolean existsByRoom_Id(Long roomId);
    long countByBranch_BranchId(String branchId);
}
