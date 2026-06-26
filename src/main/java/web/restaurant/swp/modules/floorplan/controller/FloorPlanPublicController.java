package web.restaurant.swp.modules.floorplan.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import web.restaurant.swp.modules.floorplan.model.FloorPlan;
import web.restaurant.swp.modules.floorplan.service.FloorPlanService;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class FloorPlanPublicController {

    private final FloorPlanService floorPlanService;

    /**
     * Public endpoint: get published floor plans for a branch.
     * No auth required - used by customer-facing pages.
     */
    @GetMapping("/api/public/branches/{branchId}/floor-plans")
    public ResponseEntity<?> listPublishedFloorPlans(@PathVariable String branchId) {
        try {
            List<FloorPlan> plans = floorPlanService.listPublishedFloorPlans(branchId);
            return ResponseEntity.ok(plans);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Public endpoint: get a published floor plan with all objects.
     * Only returns published floor plans.
     */
    @GetMapping("/api/public/floor-plans/{id}")
    public ResponseEntity<?> getPublishedFloorPlan(@PathVariable Long id) {
        try {
            FloorPlan plan = floorPlanService.getFloorPlanWithObjects(id);

            // Only allow viewing published floor plans via public API
            if (!"published".equals(plan.getStatus())) {
                return ResponseEntity.status(404).body(Map.of("error", "Floor plan not found or not published"));
            }

            return ResponseEntity.ok(plan);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
