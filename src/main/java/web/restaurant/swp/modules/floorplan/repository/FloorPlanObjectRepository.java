package web.restaurant.swp.modules.floorplan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import web.restaurant.swp.modules.floorplan.model.FloorPlanObject;

import java.util.List;

@Repository
public interface FloorPlanObjectRepository extends JpaRepository<FloorPlanObject, Long> {
    @Query("""
            SELECT f FROM FloorPlanObject f
            WHERE f.floorPlan.id = :floorPlanId
            ORDER BY f.zIndex ASC, f.id ASC
            """)
    List<FloorPlanObject> findByFloorPlanIdOrdered(@Param("floorPlanId") Long floorPlanId);

    List<FloorPlanObject> findByFloorPlan_IdOrderByZIndexAscIdAsc(Long floorPlanId);

    void deleteByFloorPlan_Id(Long floorPlanId);
    long countByFloorPlan_Id(Long floorPlanId);
    List<FloorPlanObject> findByTableId(Long tableId);
    boolean existsByTableId(Long tableId);

    @Modifying
    @Query("""
            DELETE FROM FloorPlanObject f
            WHERE f.tableId = :tableId
            AND (f.floorPlan.room IS NULL OR f.floorPlan.room.id <> :roomId)
            """)
    void deleteByTableIdOutsideRoom(@Param("tableId") Long tableId, @Param("roomId") Long roomId);

    @Modifying
    void deleteByTableId(Long tableId);
}
