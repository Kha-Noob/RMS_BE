package web.restaurant.swp.modules.floorplan.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import web.restaurant.swp.modules.floorplan.model.FloorPlan;
import web.restaurant.swp.modules.floorplan.model.FloorPlanObject;
import web.restaurant.swp.modules.floorplan.repository.FloorPlanObjectRepository;
import web.restaurant.swp.modules.floorplan.service.FloorPlanService;
import web.restaurant.swp.modules.pos.repository.TableRepository;
import web.restaurant.swp.modules.pos.model.TableEntity;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class FloorPlanPublicController {

    private final FloorPlanService floorPlanService;
    private final FloorPlanObjectRepository floorPlanObjectRepository;
    private final TableRepository tableRepository;

    /**
     * Public endpoint: get published floor plans for a branch.
     * No auth required - used by customer-facing pages.
     */
    @GetMapping("/api/public/branches/{branchId}/floor-plans")
    public ResponseEntity<?> listPublishedFloorPlans(@PathVariable String branchId) {
        try {
            List<FloorPlan> plans = floorPlanService.listPublishedFloorPlans(branchId);
            return ResponseEntity.ok(plans.stream().map(this::toPlanMap).toList());
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

            List<FloorPlanObject> objects = floorPlanObjectRepository
                    .findByFloorPlanIdOrdered(plan.getId());

            List<Map<String, Object>> objectList = new ArrayList<>();
            if (objects.isEmpty()) {
                objectList = getDefaultObjectsForPlan(plan);
            } else {
                for (FloorPlanObject obj : objects) {
                    objectList.add(toObjectMap(obj));
                }
            }

            Map<String, Object> planMap = toPlanMap(plan);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("floorPlan", planMap);
            response.put("objects", objectList);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> toObjectMap(FloorPlanObject obj) {
        Map<String, Object> objMap = new LinkedHashMap<>();
        objMap.put("id", obj.getId());
        objMap.put("tableId", obj.getTableId());
        objMap.put("objectType", obj.getObjectType());
        objMap.put("label", obj.getLabel());
        objMap.put("x", obj.getX());
        objMap.put("y", obj.getY());
        objMap.put("width", obj.getWidth());
        objMap.put("height", obj.getHeight());
        objMap.put("rotation", obj.getRotation());
        objMap.put("shape", obj.getShape());
        objMap.put("zIndex", obj.getZIndex());
        objMap.put("styleJson", obj.getStyleJson());
        objMap.put("metadataJson", obj.getMetadataJson());
        objMap.put("isVisible", obj.getIsVisible());
        objMap.put("isLocked", obj.getIsLocked());
        objMap.put("linked", false);
        return objMap;
    }

    private Map<String, Object> toPlanMap(FloorPlan plan) {
        Map<String, Object> planMap = new LinkedHashMap<>();
        planMap.put("id", plan.getId());
        planMap.put("name", plan.getName());
        planMap.put("floorNumber", plan.getFloorNumber());
        planMap.put("roomId", plan.getRoom() != null ? plan.getRoom().getId() : null);
        planMap.put("room", plan.getRoom() != null ? Map.of("id", plan.getRoom().getId(), "name", plan.getRoom().getName()) : null);
        planMap.put("width", plan.getWidth());
        planMap.put("height", plan.getHeight());
        planMap.put("backgroundMode", plan.getBackgroundMode());
        planMap.put("floorDiagramImageUrl", plan.getFloorDiagramImageUrl());
        planMap.put("backgroundImageUrl", plan.getFloorDiagramImageUrl());
        planMap.put("imageUrl", plan.getFloorDiagramImageUrl());
        planMap.put("floorDiagramImageKey", plan.getFloorDiagramImageKey());
        planMap.put("floorDiagramFitMode", plan.getFloorDiagramFitMode());
        planMap.put("floorDiagramX", plan.getFloorDiagramX());
        planMap.put("floorDiagramY", plan.getFloorDiagramY());
        planMap.put("floorDiagramWidth", plan.getFloorDiagramWidth());
        planMap.put("floorDiagramHeight", plan.getFloorDiagramHeight());
        planMap.put("floorDiagramScale", plan.getFloorDiagramScale());
        planMap.put("floorDiagramRotation", plan.getFloorDiagramRotation());
        planMap.put("panoramaUrl", plan.getPanoramaUrl());
        planMap.put("panoramaKey", plan.getPanoramaKey());
        planMap.put("panoramaType", plan.getPanoramaType());
        planMap.put("status", plan.getStatus());

        List<FloorPlanObject> objects = floorPlanObjectRepository.findByFloorPlanIdOrdered(plan.getId());
        if (objects.isEmpty()) {
            planMap.put("floorPlanObjects", getDefaultObjectsForPlan(plan));
        } else {
            planMap.put("floorPlanObjects", objects.stream().map(this::toObjectMap).toList());
        }

        return planMap;
    }

    private List<Map<String, Object>> getDefaultObjectsForPlan(FloorPlan plan) {
        List<Map<String, Object>> objectList = new ArrayList<>();
        if (plan.getRoom() == null) return objectList;

        List<TableEntity> tables = tableRepository.findByRoomId(plan.getRoom().getId());
        for (int i = 0; i < tables.size(); i++) {
            TableEntity table = tables.get(i);

            Map<String, Object> objMap = new LinkedHashMap<>();
            // Use negative IDs to avoid canvas key conflicts
            objMap.put("id", -(table.getId()));
            objMap.put("tableId", table.getId());
            objMap.put("objectType", "table");
            objMap.put("label", table.getName());

            // Position tables in a 4-column grid layout
            int cols = 4;
            int col = i % cols;
            int row = i / cols;
            objMap.put("x", 100.0 + col * 250.0);
            objMap.put("y", 100.0 + row * 200.0);
            objMap.put("width", 90.0);
            objMap.put("height", 90.0);
            objMap.put("rotation", 0.0);
            objMap.put("shape", "ROUND".equalsIgnoreCase(table.getTableStyle()) ? "circle" : "rectangle");
            objMap.put("zIndex", 10);
            objMap.put("styleJson", Map.of("fillColor", "#22c55e"));
            objMap.put("metadataJson", Map.of(
                "tableEntityId", table.getId(),
                "tableId", table.getId(),
                "linkedTableId", table.getId(),
                "tableName", table.getName(),
                "capacity", table.getCapacity()
            ));
            objMap.put("isVisible", true);
            objMap.put("isLocked", false);
            objMap.put("linked", true);
            objectList.add(objMap);
        }
        return objectList;
    }
}
