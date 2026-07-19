package web.restaurant.swp.modules.menu.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import web.restaurant.swp.modules.menu.dto.*;
import web.restaurant.swp.modules.menu.service.MenuService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
@Slf4j
public class MenuController {

    private final MenuService menuService;

    // ─── Menu Items ────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<MenuItemResponse>> getAllMenuItems() {
        return ResponseEntity.ok(menuService.getAllMenuItems());
    }

    @GetMapping("/active")
    public ResponseEntity<List<MenuItemResponse>> getActiveMenuItems() {
        return ResponseEntity.ok(menuService.getActiveMenuItems());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMenuItemById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(menuService.getMenuItemById(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createMenuItem(@RequestBody MenuItemRequest request) {
        try {
            MenuItemResponse response = menuService.createMenuItem(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMenuItem(@PathVariable Long id, @RequestBody MenuItemRequest request) {
        try {
            MenuItemResponse response = menuService.updateMenuItem(id, request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> patchMenuItemStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        try {
            MenuItemResponse response = menuService.patchMenuItemStatus(id, request.getStatus());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMenuItem(@PathVariable Long id) {
        try {
            menuService.deleteMenuItem(id);
            return ResponseEntity.ok(Map.of("message", "Đã xóa món ăn thành công"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<?> deleteMenuItemsBulk(@RequestBody List<Long> ids) {
        try {
            for (Long id : ids) {
                menuService.deleteMenuItem(id);
            }
            return ResponseEntity.ok(Map.of("message", "Đã xóa các món ăn thành công"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Categories ────────────────────────────────────────────────────────

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(menuService.getAllCategories());
    }

    @PostMapping("/categories")
    public ResponseEntity<?> createCategory(@RequestBody CategoryRequest request) {
        try {
            CategoryResponse response = menuService.createCategory(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable Long id, @RequestBody CategoryRequest request) {
        try {
            CategoryResponse response = menuService.updateCategory(id, request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        try {
            menuService.deleteCategory(id);
            return ResponseEntity.ok(Map.of("message", "Đã xóa danh mục thành công"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
