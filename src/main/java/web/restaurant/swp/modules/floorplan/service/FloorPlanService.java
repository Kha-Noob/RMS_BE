package web.restaurant.swp.modules.floorplan.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.branch.model.Branch;
import web.restaurant.swp.modules.branch.repository.BranchRepository;
import web.restaurant.swp.modules.floorplan.model.FloorPlan;
import web.restaurant.swp.modules.floorplan.model.FloorPlanObject;
import web.restaurant.swp.modules.floorplan.repository.FloorPlanObjectRepository;
import web.restaurant.swp.modules.floorplan.repository.FloorPlanRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FloorPlanService {

    private final FloorPlanRepository floorPlanRepository;
    private final FloorPlanObjectRepository floorPlanObjectRepository;
    private final BranchRepository branchRepository;

    // ─── Floor Plan CRUD ───────────────────────────────────────────────

    public List<FloorPlan> listFloorPlans(String branchId) {
        return floorPlanRepository.findByBranch_BranchIdOrderByFloorNumberAsc(branchId);
    }

    public List<FloorPlan> listPublishedFloorPlans(String branchId) {
        return floorPlanRepository.findByBranch_BranchIdAndStatusOrderByFloorNumberAsc(branchId, "published");
    }

    public FloorPlan getFloorPlan(Long id) {
        return floorPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Floor plan not found: " + id));
    }

    public FloorPlan getFloorPlanWithObjects(Long id) {
        FloorPlan plan = getFloorPlan(id);
        // Objects are lazily loaded but we access them here to ensure they're loaded within transaction
        plan.getFloorPlanObjects().size();
        return plan;
    }

    @Transactional
    public FloorPlan createFloorPlan(String branchId, String name, Integer floorNumber,
                                      Integer width, Integer height, User creator) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found: " + branchId));

        FloorPlan plan = FloorPlan.builder()
                .branch(branch)
                .name(name)
                .floorNumber(floorNumber != null ? floorNumber : 1)
                .width(width != null ? width : 1200)
                .height(height != null ? height : 800)
                .status("draft")
                .isTableSelectionEnabled(false)
                .createdBy(creator)
                .updatedBy(creator)
                .build();

        return floorPlanRepository.save(plan);
    }

    @Transactional
    public FloorPlan updateFloorPlan(Long id, Map<String, Object> updates, User updater) {
        FloorPlan plan = getFloorPlan(id);

        if (updates.containsKey("name")) plan.setName((String) updates.get("name"));
        if (updates.containsKey("floorNumber")) plan.setFloorNumber((Integer) updates.get("floorNumber"));
        if (updates.containsKey("width")) plan.setWidth((Integer) updates.get("width"));
        if (updates.containsKey("height")) plan.setHeight((Integer) updates.get("height"));
        if (updates.containsKey("backgroundImageUrl")) plan.setBackgroundImageUrl((String) updates.get("backgroundImageUrl"));
        if (updates.containsKey("panorama360Url")) plan.setPanorama360Url((String) updates.get("panorama360Url"));
        if (updates.containsKey("isTableSelectionEnabled")) plan.setIsTableSelectionEnabled((Boolean) updates.get("isTableSelectionEnabled"));
        plan.setUpdatedBy(updater);

        return floorPlanRepository.save(plan);
    }

    @Transactional
    public void deleteFloorPlan(Long id) {
        FloorPlan plan = getFloorPlan(id);
        if (!"draft".equals(plan.getStatus())) {
            throw new RuntimeException("Only draft floor plans can be deleted");
        }
        floorPlanObjectRepository.deleteByFloorPlan_Id(id);
        floorPlanRepository.delete(plan);
    }

    @Transactional
    public FloorPlan publishFloorPlan(Long id, User updater) {
        FloorPlan plan = getFloorPlan(id);
        plan.setStatus("published");
        plan.setUpdatedBy(updater);
        return floorPlanRepository.save(plan);
    }

    @Transactional
    public FloorPlan unpublishFloorPlan(Long id, User updater) {
        FloorPlan plan = getFloorPlan(id);
        plan.setStatus("draft");
        plan.setUpdatedBy(updater);
        return floorPlanRepository.save(plan);
    }

    @Transactional
    public FloorPlan updateBackgroundImage(Long id, String imageUrl, User updater) {
        FloorPlan plan = getFloorPlan(id);
        plan.setBackgroundImageUrl(imageUrl);
        plan.setUpdatedBy(updater);
        return floorPlanRepository.save(plan);
    }

    @Transactional
    public FloorPlan update360Image(Long id, String imageUrl, User updater) {
        FloorPlan plan = getFloorPlan(id);
        plan.setPanorama360Url(imageUrl);
        plan.setUpdatedBy(updater);
        return floorPlanRepository.save(plan);
    }

    // ─── Floor Plan Objects CRUD ───────────────────────────────────────

    public List<FloorPlanObject> listObjects(Long floorPlanId) {
        return floorPlanObjectRepository.findByFloorPlan_IdOrderByZIndexAscIdAsc(floorPlanId);
    }

    public FloorPlanObject getObject(Long objectId) {
        return floorPlanObjectRepository.findById(objectId)
                .orElseThrow(() -> new RuntimeException("Floor plan object not found: " + objectId));
    }

    @Transactional
    public FloorPlanObject createObject(Long floorPlanId, String objectType, String label,
                                         Double x, Double y, Double width, Double height,
                                         Double rotation, String shape, Integer zIndex,
                                         String styleJson, String metadataJson) {
        FloorPlan plan = getFloorPlan(floorPlanId);

        FloorPlanObject obj = FloorPlanObject.builder()
                .floorPlan(plan)
                .objectType(objectType)
                .label(label)
                .x(x != null ? x : 0.0)
                .y(y != null ? y : 0.0)
                .width(width != null ? width : 80.0)
                .height(height != null ? height : 80.0)
                .rotation(rotation != null ? rotation : 0.0)
                .shape(shape)
                .zIndex(zIndex != null ? zIndex : 0)
                .styleJson(styleJson)
                .metadataJson(metadataJson)
                .build();

        return floorPlanObjectRepository.save(obj);
    }

    @Transactional
    public FloorPlanObject updateObject(Long objectId, Map<String, Object> updates) {
        FloorPlanObject obj = getObject(objectId);

        if (updates.containsKey("label")) obj.setLabel((String) updates.get("label"));
        if (updates.containsKey("objectType")) obj.setObjectType((String) updates.get("objectType"));
        if (updates.containsKey("x")) obj.setX(((Number) updates.get("x")).doubleValue());
        if (updates.containsKey("y")) obj.setY(((Number) updates.get("y")).doubleValue());
        if (updates.containsKey("width")) obj.setWidth(((Number) updates.get("width")).doubleValue());
        if (updates.containsKey("height")) obj.setHeight(((Number) updates.get("height")).doubleValue());
        if (updates.containsKey("rotation")) obj.setRotation(((Number) updates.get("rotation")).doubleValue());
        if (updates.containsKey("shape")) obj.setShape((String) updates.get("shape"));
        if (updates.containsKey("zIndex")) obj.setZIndex((Integer) updates.get("zIndex"));
        if (updates.containsKey("styleJson")) obj.setStyleJson((String) updates.get("styleJson"));
        if (updates.containsKey("metadataJson")) obj.setMetadataJson((String) updates.get("metadataJson"));

        return floorPlanObjectRepository.save(obj);
    }

    @Transactional
    public void deleteObject(Long objectId) {
        FloorPlanObject obj = getObject(objectId);
        floorPlanObjectRepository.delete(obj);
    }

    @Transactional
    public List<FloorPlanObject> bulkUpdateObjects(Long floorPlanId, List<Map<String, Object>> objectDataList) {
        FloorPlan plan = getFloorPlan(floorPlanId);

        // Delete existing objects for this floor plan
        floorPlanObjectRepository.deleteByFloorPlan_Id(floorPlanId);
        floorPlanRepository.flush(); // Ensure delete is executed before inserts

        // Create all new objects
        List<FloorPlanObject> savedObjects = new ArrayList<>();
        for (Map<String, Object> data : objectDataList) {
            FloorPlanObject obj = FloorPlanObject.builder()
                    .floorPlan(plan)
                    .objectType((String) data.get("objectType"))
                    .label((String) data.get("label"))
                    .x(data.get("x") != null ? ((Number) data.get("x")).doubleValue() : 0.0)
                    .y(data.get("y") != null ? ((Number) data.get("y")).doubleValue() : 0.0)
                    .width(data.get("width") != null ? ((Number) data.get("width")).doubleValue() : 80.0)
                    .height(data.get("height") != null ? ((Number) data.get("height")).doubleValue() : 80.0)
                    .rotation(data.get("rotation") != null ? ((Number) data.get("rotation")).doubleValue() : 0.0)
                    .shape((String) data.get("shape"))
                    .zIndex(data.get("zIndex") != null ? ((Number) data.get("zIndex")).intValue() : 0)
                    .styleJson(data.get("styleJson") != null ? data.get("styleJson").toString() : null)
                    .metadataJson(data.get("metadataJson") != null ? data.get("metadataJson").toString() : null)
                    .build();

            savedObjects.add(floorPlanObjectRepository.save(obj));
        }

        log.info("Bulk updated {} objects for floor plan {}", savedObjects.size(), floorPlanId);
        return savedObjects;
    }
}
