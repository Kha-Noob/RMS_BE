package web.restaurant.swp.modules.floorplan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.restaurant.swp.modules.floorplan.model.FloorPlanObject;

import java.util.List;

@Repository
public interface FloorPlanObjectRepository extends JpaRepository<FloorPlanObject, Long> {
    List<FloorPlanObject> findByFloorPlan_IdOrderByZIndexAscIdAsc(Long floorPlanId);
    void deleteByFloorPlan_Id(Long floorPlanId);
    long countByFloorPlan_Id(Long floorPlanId);
}
