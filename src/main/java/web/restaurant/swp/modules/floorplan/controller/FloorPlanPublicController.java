package web.restaurant.swp.modules.floorplan.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import web.restaurant.swp.modules.floorplan.model.FloorPlan;
import web.restaurant.swp.modules.floorplan.model.FloorPlanObject;
import web.restaurant.swp.modules.floorplan.repository.FloorPlanObjectRepository;
import web.restaurant.swp.modules.floorplan.service.FloorPlanService;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class FloorPlanPublicController {

    private final FloorPlanService floorPlanService;
    private final FloorPlanObjectRepository floorPlanObjectRepository;

    @GetMapping("/api/public/branches/{branchId}/floor-plans")
    public ResponseEntity<?> listPublishedFloorPlans(@PathVariable String branchId) {
        try {
            List<FloorPlan> plans = floorPlanService.listPublishedFloorPlans(branchId);
            return ResponseEntity.ok(plans.stream().map(this::toPlanMap).toList());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/public/floor-plans/{id}")
    public ResponseEntity<?> getPublishedFloorPlan(@PathVariable Long id) {
        try {
            FloorPlan plan = floorPlanService.getFloorPlan(id);
            if (!"published".equals(plan.getStatus())) {
                return ResponseEntity.status(404).body(Map.of("error", "Floor plan not found or not published"));
            }

            List<FloorPlanObject> objects = floorPlanObjectRepository
                    .findByFloorPlanIdOrdered(plan.getId());

            List<Map<String, Object>> objectList = new ArrayList<>();
            for (FloorPlanObject obj : objects) {
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
                objectList.add(objMap);
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
        return planMap;
    }
}
