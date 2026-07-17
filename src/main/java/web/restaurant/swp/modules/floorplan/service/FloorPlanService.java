package web.restaurant.swp.modules.floorplan.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import web.restaurant.swp.modules.pos.model.Room;
import web.restaurant.swp.modules.pos.model.TableEntity;
import web.restaurant.swp.modules.pos.repository.RoomRepository;
import web.restaurant.swp.modules.pos.repository.TableRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FloorPlanService {

    private final FloorPlanRepository floorPlanRepository;
    private final FloorPlanObjectRepository floorPlanObjectRepository;
    private final BranchRepository branchRepository;
    private final RoomRepository roomRepository;
    private final TableRepository tableRepository;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> JSON_MAP_TYPE = new TypeReference<>() {};

    // ─── Floor Plan CRUD ───────────────────────────────────────────────

    public List<FloorPlan> listFloorPlans(String branchId) {
        return floorPlanRepository.findByBranch_BranchIdOrderByRoom_DisplayOrderAscNameAsc(branchId);
    }

    public List<FloorPlan> listPublishedFloorPlans(String branchId) {
        return floorPlanRepository.findByBranch_BranchIdAndStatusOrderByRoom_DisplayOrderAscNameAsc(branchId, "published");
    }

    public FloorPlan getActiveFloorPlanForRoom(Long roomId) {
        return floorPlanRepository.findByRoom_IdAndStatusOrderByIdAsc(roomId, "published")
                .stream()
                .findFirst()
                .orElse(null);
    }

    public FloorPlan getFloorPlan(Long id) {
        return floorPlanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Floor plan not found: " + id));
    }

    public FloorPlan getFloorPlanWithObjects(Long id) {
        FloorPlan plan = getFloorPlan(id);
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
    public FloorPlan createFloorPlan(String branchId, Long roomId, String name, Integer floorNumber,
                                      Integer width, Integer height, String backgroundMode, User creator) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found: " + branchId));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room/area not found: " + roomId));

        if (!room.getBranch().getBranchId().equals(branchId)) {
            throw new IllegalArgumentException("Room/area does not belong to this branch");
        }
        boolean alreadyHasPlan = floorPlanRepository.findByRoom_IdOrderByIdAsc(roomId).stream()
                .anyMatch(fp -> !"archived".equalsIgnoreCase(fp.getStatus()));
        if (alreadyHasPlan) {
            throw new IllegalArgumentException("This room/area already has a floor plan");
        }

        FloorPlan plan = FloorPlan.builder()
                .branch(branch)
                .room(room)
                .name(name)
                .floorNumber(floorNumber != null ? floorNumber : (room.getDisplayOrder() != null ? room.getDisplayOrder() + 1 : 1))
                .width(width != null ? width : 1200)
                .height(height != null ? height : 800)
                .backgroundMode(backgroundMode != null ? backgroundMode : "DEFAULT_WOOD")
                .status("draft")
                .createdBy(creator)
                .updatedBy(creator)
                .build();

        return floorPlanRepository.save(plan);
    }

    @Transactional
    public FloorPlan updateFloorPlan(Long id, Map<String, Object> updates, User updater) {
        FloorPlan plan = getFloorPlan(id);

        if (updates.containsKey("name")) plan.setName((String) updates.get("name"));
        if (updates.containsKey("roomId")) {
            Long roomId = toLong(updates.get("roomId"));
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Room/area not found: " + roomId));
            if (!room.getBranch().getBranchId().equals(plan.getBranch().getBranchId())) {
                throw new IllegalArgumentException("Room/area does not belong to this branch");
            }
            plan.setRoom(room);
        }
        if (updates.containsKey("floorNumber")) plan.setFloorNumber(((Number) updates.get("floorNumber")).intValue());
        if (updates.containsKey("width")) plan.setWidth(((Number) updates.get("width")).intValue());
        if (updates.containsKey("height")) plan.setHeight(((Number) updates.get("height")).intValue());
        if (updates.containsKey("floorDiagramImageUrl")) plan.setFloorDiagramImageUrl((String) updates.get("floorDiagramImageUrl"));
        if (updates.containsKey("floorDiagramImageKey")) plan.setFloorDiagramImageKey((String) updates.get("floorDiagramImageKey"));
        if (updates.containsKey("floorDiagramFitMode")) plan.setFloorDiagramFitMode((String) updates.get("floorDiagramFitMode"));
        if (updates.containsKey("floorDiagramX")) plan.setFloorDiagramX(toDouble(updates.get("floorDiagramX")));
        if (updates.containsKey("floorDiagramY")) plan.setFloorDiagramY(toDouble(updates.get("floorDiagramY")));
        if (updates.containsKey("floorDiagramWidth")) plan.setFloorDiagramWidth(toDouble(updates.get("floorDiagramWidth")));
        if (updates.containsKey("floorDiagramHeight")) plan.setFloorDiagramHeight(toDouble(updates.get("floorDiagramHeight")));
        if (updates.containsKey("floorDiagramScale")) plan.setFloorDiagramScale(toDouble(updates.get("floorDiagramScale")));
        if (updates.containsKey("floorDiagramRotation")) plan.setFloorDiagramRotation(toDouble(updates.get("floorDiagramRotation")));
        if (updates.containsKey("backgroundMode")) plan.setBackgroundMode((String) updates.get("backgroundMode"));
        if (updates.containsKey("panoramaUrl")) plan.setPanoramaUrl((String) updates.get("panoramaUrl"));
        if (updates.containsKey("panoramaKey")) plan.setPanoramaKey((String) updates.get("panoramaKey"));
        if (updates.containsKey("panoramaType")) plan.setPanoramaType((String) updates.get("panoramaType"));
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
    public FloorPlan updateFloorDiagramImage(Long id, String imageUrl, User updater) {
        FloorPlan plan = getFloorPlan(id);
        plan.setFloorDiagramImageUrl(imageUrl);
        plan.setBackgroundMode("CUSTOM_IMAGE");
        resetFloorDiagramTransform(plan);
        plan.setUpdatedBy(updater);
        return floorPlanRepository.save(plan);
    }

    @Transactional
    public FloorPlan updateFloorDiagramImage(Long id, String imageUrl, String imageKey, User updater) {
        FloorPlan plan = getFloorPlan(id);
        plan.setFloorDiagramImageUrl(imageUrl);
        plan.setFloorDiagramImageKey(imageKey);
        plan.setBackgroundMode("CUSTOM_IMAGE");
        resetFloorDiagramTransform(plan);
        plan.setUpdatedBy(updater);
        return floorPlanRepository.save(plan);
    }

    @Transactional
    public FloorPlan updateBackgroundImage(Long id, String imageUrl, User updater) {
        FloorPlan plan = getFloorPlan(id);
        plan.setBackgroundMode("CUSTOM_IMAGE");
        plan.setUpdatedBy(updater);
        return floorPlanRepository.save(plan);
    }

    @Transactional
    public FloorPlan update360Image(Long id, String imageUrl, User updater) {
        FloorPlan plan = getFloorPlan(id);
        plan.setPanoramaUrl(imageUrl);
        plan.setPanoramaType("IMAGE_360");
        plan.setUpdatedBy(updater);
        return floorPlanRepository.save(plan);
    }

    @Transactional
    public FloorPlan updatePanoramaImage(Long id, String imageUrl, User updater) {
        FloorPlan plan = getFloorPlan(id);
        plan.setPanoramaUrl(imageUrl);
        plan.setPanoramaType("IMAGE_360");
        plan.setUpdatedBy(updater);
        return floorPlanRepository.save(plan);
    }

    @Transactional
    public FloorPlan updatePanoramaImage(Long id, String imageUrl, String panoramaKey, User updater) {
        FloorPlan plan = getFloorPlan(id);
        plan.setPanoramaUrl(imageUrl);
        plan.setPanoramaKey(panoramaKey);
        plan.setPanoramaType("IMAGE_360");
        plan.setUpdatedBy(updater);
        return floorPlanRepository.save(plan);
    }

    // ─── Floor Plan Object CRUD ───────────────────────────────────────

    public List<FloorPlanObject> listObjects(Long floorPlanId) {
        return floorPlanObjectRepository.findByFloorPlanIdOrdered(floorPlanId);
    }

    public FloorPlanObject getObject(Long id) {
        return floorPlanObjectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Object not found: " + id));
    }

    @Transactional
    public FloorPlanObject createObject(Long floorPlanId, Map<String, Object> data) {
        FloorPlan plan = getFloorPlan(floorPlanId);
        Map<String, Object> metadataJson = objectMetadata(data);
        String objectType = (String) data.get("objectType");
        String label = (String) data.get("label");
        Double x = data.get("x") != null ? ((Number) data.get("x")).doubleValue() : 0.0;
        Double y = data.get("y") != null ? ((Number) data.get("y")).doubleValue() : 0.0;
        Double width = data.get("width") != null ? ((Number) data.get("width")).doubleValue() : 80.0;
        Double height = data.get("height") != null ? ((Number) data.get("height")).doubleValue() : 80.0;
        Double rotation = data.get("rotation") != null ? ((Number) data.get("rotation")).doubleValue() : 0.0;

        Long[] tableIdHolder = new Long[1];
        metadataJson = syncTableEntityForObject(plan, objectType, label, x, y, width, height, rotation, metadataJson, tableIdHolder);
        validateObjectTableBelongsToRoom(plan, metadataJson);

        FloorPlanObject obj = FloorPlanObject.builder()
                .floorPlan(plan)
                .tableId(tableIdHolder[0])
                .objectType(objectType)
                .label(label)
                .x(x)
                .y(y)
                .width(width)
                .height(height)
                .rotation(rotation)
                .shape((String) data.get("shape"))
                .zIndex(data.get("zIndex") != null ? ((Number) data.get("zIndex")).intValue() : 0)
                .styleJson(toJsonMap(data.get("styleJson")))
                .metadataJson(metadataJson)
                .isVisible(data.get("isVisible") != null ? Boolean.parseBoolean(data.get("isVisible").toString()) : true)
                .isLocked(data.get("isLocked") != null ? Boolean.parseBoolean(data.get("isLocked").toString()) : false)
                .build();

        return floorPlanObjectRepository.save(obj);
    }

    @Transactional
    public FloorPlanObject updateObject(Long id, Map<String, Object> data) {
        FloorPlanObject obj = getObject(id);

        if (data.containsKey("objectType")) obj.setObjectType((String) data.get("objectType"));
        if (data.containsKey("label")) obj.setLabel((String) data.get("label"));
        if (data.containsKey("x")) obj.setX(((Number) data.get("x")).doubleValue());
        if (data.containsKey("y")) obj.setY(((Number) data.get("y")).doubleValue());
        if (data.containsKey("width")) obj.setWidth(((Number) data.get("width")).doubleValue());
        if (data.containsKey("height")) obj.setHeight(((Number) data.get("height")).doubleValue());
        if (data.containsKey("rotation")) obj.setRotation(((Number) data.get("rotation")).doubleValue());
        if (data.containsKey("shape")) obj.setShape((String) data.get("shape"));
        if (data.containsKey("zIndex")) obj.setZIndex(((Number) data.get("zIndex")).intValue());
        if (data.containsKey("styleJson")) obj.setStyleJson(toJsonMap(data.get("styleJson")));
        if (data.containsKey("metadataJson") || data.containsKey("tableId")) {
            Map<String, Object> metadataJson = objectMetadata(data);
            Long[] tableIdHolder = new Long[1];
            metadataJson = syncTableEntityForObject(obj.getFloorPlan(), obj.getObjectType(), obj.getLabel(),
                    obj.getX(), obj.getY(), obj.getWidth(), obj.getHeight(), obj.getRotation(),
                    metadataJson, tableIdHolder);
            validateObjectTableBelongsToRoom(obj.getFloorPlan(), metadataJson);
            obj.setMetadataJson(metadataJson);
            obj.setTableId(tableIdHolder[0]);
        }
        if (data.containsKey("isVisible")) obj.setIsVisible(Boolean.parseBoolean(data.get("isVisible").toString()));
        if (data.containsKey("isLocked")) obj.setIsLocked(Boolean.parseBoolean(data.get("isLocked").toString()));

        return floorPlanObjectRepository.save(obj);
    }

    @Transactional
    public void deleteObject(Long id) {
        FloorPlanObject obj = getObject(id);
        floorPlanObjectRepository.delete(obj);
    }

    @Transactional
    public List<FloorPlanObject> bulkSaveObjects(Long floorPlanId, List<Map<String, Object>> objectsData) {
        FloorPlan plan = getFloorPlan(floorPlanId);

        floorPlanObjectRepository.deleteByFloorPlan_Id(floorPlanId);
        floorPlanRepository.flush();

        List<FloorPlanObject> saved = new ArrayList<>();
        for (Map<String, Object> data : objectsData) {
            Map<String, Object> metadataJson = objectMetadata(data);
            String objectType = (String) data.get("objectType");
            String label = (String) data.get("label");
            Double x = data.get("x") != null ? ((Number) data.get("x")).doubleValue() : 0.0;
            Double y = data.get("y") != null ? ((Number) data.get("y")).doubleValue() : 0.0;
            Double width = data.get("width") != null ? ((Number) data.get("width")).doubleValue() : 80.0;
            Double height = data.get("height") != null ? ((Number) data.get("height")).doubleValue() : 80.0;
            Double rotation = data.get("rotation") != null ? ((Number) data.get("rotation")).doubleValue() : 0.0;

            Long[] tableIdHolder = new Long[1];
            metadataJson = syncTableEntityForObject(plan, objectType, label, x, y, width, height, rotation, metadataJson, tableIdHolder);
            validateObjectTableBelongsToRoom(plan, metadataJson);

            FloorPlanObject obj = FloorPlanObject.builder()
                    .floorPlan(plan)
                    .tableId(tableIdHolder[0])
                    .objectType(objectType)
                    .label(label)
                    .x(x)
                    .y(y)
                    .width(width)
                    .height(height)
                    .rotation(rotation)
                    .shape((String) data.get("shape"))
                    .zIndex(data.get("zIndex") != null ? ((Number) data.get("zIndex")).intValue() : 0)
                    .styleJson(toJsonMap(data.get("styleJson")))
                    .metadataJson(metadataJson)
                    .isVisible(data.get("isVisible") != null ? Boolean.parseBoolean(data.get("isVisible").toString()) : true)
                    .isLocked(data.get("isLocked") != null ? Boolean.parseBoolean(data.get("isLocked").toString()) : false)
                    .build();
            saved.add(floorPlanObjectRepository.save(obj));
        }

        log.info("Bulk saved {} objects for floor plan {}", saved.size(), floorPlanId);
        return saved;
    }

    @Transactional
    public List<FloorPlanObject> bulkUpdateObjects(Long floorPlanId, List<Map<String, Object>> objectDataList) {
        FloorPlan plan = getFloorPlan(floorPlanId);

        floorPlanObjectRepository.deleteByFloorPlan_Id(floorPlanId);
        floorPlanRepository.flush();

        List<FloorPlanObject> savedObjects = new ArrayList<>();
        for (Map<String, Object> data : objectDataList) {
            Map<String, Object> metadataJson = objectMetadata(data);
            String objectType = (String) data.get("objectType");
            String label = (String) data.get("label");
            Double x = data.get("x") != null ? ((Number) data.get("x")).doubleValue() : 0.0;
            Double y = data.get("y") != null ? ((Number) data.get("y")).doubleValue() : 0.0;
            Double width = data.get("width") != null ? ((Number) data.get("width")).doubleValue() : 80.0;
            Double height = data.get("height") != null ? ((Number) data.get("height")).doubleValue() : 80.0;
            Double rotation = data.get("rotation") != null ? ((Number) data.get("rotation")).doubleValue() : 0.0;

            Long[] tableIdHolder = new Long[1];
            metadataJson = syncTableEntityForObject(plan, objectType, label, x, y, width, height, rotation, metadataJson, tableIdHolder);
            validateObjectTableBelongsToRoom(plan, metadataJson);

            FloorPlanObject obj = FloorPlanObject.builder()
                    .floorPlan(plan)
                    .tableId(tableIdHolder[0])
                    .objectType(objectType)
                    .label(label)
                    .x(x)
                    .y(y)
                    .width(width)
                    .height(height)
                    .rotation(rotation)
                    .shape((String) data.get("shape"))
                    .zIndex(data.get("zIndex") != null ? ((Number) data.get("zIndex")).intValue() : 0)
                    .styleJson(toJsonMap(data.get("styleJson")))
                    .metadataJson(metadataJson)
                    .build();

            savedObjects.add(floorPlanObjectRepository.save(obj));
        }

        log.info("Bulk updated {} objects for floor plan {}", savedObjects.size(), floorPlanId);
        return savedObjects;
    }

    @Transactional
    public void syncAllTablesForFloorPlan(Long floorPlanId) {
        FloorPlan plan = getFloorPlan(floorPlanId);
        List<FloorPlanObject> objects = floorPlanObjectRepository.findByFloorPlanIdOrdered(floorPlanId);
        for (FloorPlanObject obj : objects) {
            if (isTableObjectType(obj.getObjectType())) {
                Long[] tableIdHolder = new Long[1];
                Map<String, Object> metadata = obj.getMetadataJson();
                metadata = syncTableEntityForObject(plan, obj.getObjectType(), obj.getLabel(),
                        obj.getX(), obj.getY(), obj.getWidth(), obj.getHeight(), obj.getRotation(),
                        metadata, tableIdHolder);
                obj.setMetadataJson(metadata);
                obj.setTableId(tableIdHolder[0]);
                floorPlanObjectRepository.save(obj);
            }
        }
        log.info("Synchronized table entities for floor plan: {}", floorPlanId);
    }

    private boolean isTableObjectType(String objectType) {
        if (objectType == null) return false;
        String type = objectType.toLowerCase();
        return "table".equals(type)
                || type.startsWith("round_table_")
                || type.startsWith("square_table_")
                || type.startsWith("rectangle_table_")
                || "vip_sofa".equals(type)
                || "booth".equals(type);
    }

    private String determineTableStyle(String objectType, Map<String, Object> metadataJson) {
        if (metadataJson != null && metadataJson.get("tableStyle") != null) {
            return metadataJson.get("tableStyle").toString();
        }
        if (objectType == null) return "ROUND";
        String type = objectType.toLowerCase();
        if (type.contains("square")) return "SQUARE";
        if (type.contains("rectangle")) return "RECTANGLE";
        if (type.contains("vip")) return "VIP";
        return "ROUND";
    }

    private Map<String, Object> syncTableEntityForObject(FloorPlan plan, String objectType, String label, Double x, Double y, Double width, Double height, Double rotation, Map<String, Object> metadataJson, Long[] tableIdHolder) {
        if (!isTableObjectType(objectType)) {
            tableIdHolder[0] = extractTableId(metadataJson);
            return metadataJson;
        }

        Long tableId = extractTableId(metadataJson);
        TableEntity table = null;
        if (tableId != null) {
            table = tableRepository.findById(tableId).orElse(null);
        }
        if (table == null) {
            table = new TableEntity();
            table.setStatus("EMPTY");
            table.setGuestCount(0);
        }

        table.setName(label != null && !label.isBlank() ? label : "Bàn");
        table.setRoom(plan.getRoom());

        Integer capacity = 4;
        if (metadataJson != null && metadataJson.get("capacity") != null) {
            try {
                capacity = Integer.parseInt(metadataJson.get("capacity").toString());
            } catch (Exception ignored) {}
        }
        table.setCapacity(capacity);

        String style = determineTableStyle(objectType, metadataJson);
        table.setTableStyle(style);
        table.setShape("ROUND".equalsIgnoreCase(style) ? "circle" : "rectangle");

        table.setLayoutX(x != null ? x : 0.0);
        table.setLayoutY(y != null ? y : 0.0);
        table.setLayoutWidth(width != null ? width : 80.0);
        table.setLayoutHeight(height != null ? height : 80.0);
        table.setLayoutRotation(rotation != null ? rotation : 0.0);

        table = tableRepository.save(table);
        tableId = table.getId();

        if (metadataJson == null) {
            metadataJson = new LinkedHashMap<>();
        }
        metadataJson.put("tableEntityId", tableId);
        metadataJson.put("tableId", tableId);
        metadataJson.put("linkedTableId", tableId);
        metadataJson.put("tableName", table.getName());
        metadataJson.put("capacity", table.getCapacity());
        metadataJson.put("tableStyle", table.getTableStyle());

        tableIdHolder[0] = tableId;
        return metadataJson;
    }

    private void validateObjectTableBelongsToRoom(FloorPlan plan, Map<String, Object> metadataJson) {
        if (plan.getRoom() == null || metadataJson == null) {
            return;
        }
        Object tableIdValue = metadataJson.get("tableEntityId");
        if (tableIdValue == null) tableIdValue = metadataJson.get("tableId");
        if (tableIdValue == null) tableIdValue = metadataJson.get("linkedTableId");
        if (tableIdValue == null) {
            return;
        }
        Long tableId = toLong(tableIdValue);
        TableEntity table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Linked table not found: " + tableId));
        if (!table.getRoom().getId().equals(plan.getRoom().getId())) {
            throw new IllegalArgumentException("Table " + table.getName() + " does not belong to room/area " + plan.getRoom().getName());
        }
        metadataJson.put("tableEntityId", tableId);
        metadataJson.put("tableId", tableId);
    }

    private Long extractTableId(Map<String, Object> metadataJson) {
        if (metadataJson == null) return null;
        Object tableIdValue = metadataJson.get("tableEntityId");
        if (tableIdValue == null) tableIdValue = metadataJson.get("tableId");
        if (tableIdValue == null) tableIdValue = metadataJson.get("linkedTableId");
        return tableIdValue != null ? toLong(tableIdValue) : null;
    }

    private Map<String, Object> objectMetadata(Map<String, Object> data) {
        Map<String, Object> metadataJson = toJsonMap(data.get("metadataJson"));
        if (data.containsKey("tableId") && data.get("tableId") != null) {
            if (metadataJson == null) {
                metadataJson = new LinkedHashMap<>();
            }
            Long tableId = toLong(data.get("tableId"));
            metadataJson.put("tableId", tableId);
            metadataJson.put("tableEntityId", tableId);
        }
        return metadataJson;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toJsonMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        if (value instanceof String text) {
            if (text.isBlank()) {
                return null;
            }
            try {
                return OBJECT_MAPPER.readValue(text, JSON_MAP_TYPE);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON object: " + text, e);
            }
        }
        throw new IllegalArgumentException("JSON value must be an object");
    }

    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.doubleValue();
        return Double.parseDouble(value.toString());
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(value.toString());
    }

    private void resetFloorDiagramTransform(FloorPlan plan) {
        plan.setFloorDiagramFitMode("contain");
        plan.setFloorDiagramX(0.0);
        plan.setFloorDiagramY(0.0);
        plan.setFloorDiagramWidth(100.0);
        plan.setFloorDiagramHeight(100.0);
        plan.setFloorDiagramScale(1.0);
        plan.setFloorDiagramRotation(0.0);
    }
}
