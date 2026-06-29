package web.restaurant.swp.modules.event.controller;

import web.restaurant.swp.modules.event.model.Event;
import web.restaurant.swp.modules.event.repository.EventRepository;
import web.restaurant.swp.modules.auth.model.User;
import web.restaurant.swp.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class EventController {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    private String getLoggedInUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private Map<String, Object> toEventDto(Event e) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", e.getId());
        map.put("title", e.getTitle());
        map.put("date", e.getDate());
        map.put("time", e.getTime());
        map.put("location", e.getLocation());
        map.put("restaurantName", e.getRestaurantName());
        map.put("tag", e.getTag());
        map.put("imageUrl", e.getImageUrl());
        map.put("price", e.getPrice());
        map.put("capacity", e.getCapacity());
        map.put("description", e.getDescription());
        map.put("highlights", e.getHighlights() != null && !e.getHighlights().trim().isEmpty() 
            ? Arrays.asList(e.getHighlights().split(";")) 
            : Collections.emptyList());
        map.put("branchId", e.getBranchId());
        map.put("eventDates", e.getEventDates() != null && !e.getEventDates().trim().isEmpty() 
            ? Arrays.asList(e.getEventDates().split(",")) 
            : Collections.emptyList());
        map.put("status", e.getStatus());
        map.put("commissionRate", e.getCommissionRate());
        map.put("isUsingSystemWeb", e.getIsUsingSystemWeb());
        map.put("createdBy", e.getCreatedBy());
        return map;
    }

    @GetMapping("/public")
    public ResponseEntity<?> getPublicEvents() {
        List<Event> list = eventRepository.findByStatusOrderByCreatedAtDesc("APPROVED");
        return ResponseEntity.ok(list.stream().map(this::toEventDto).collect(Collectors.toList()));
    }

    @GetMapping("/admin/all")
    public ResponseEntity<?> getAdminAllEvents() {
        List<Event> list = eventRepository.findAll();
        return ResponseEntity.ok(list.stream().map(this::toEventDto).collect(Collectors.toList()));
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyEvents() {
        String email = getLoggedInUserEmail();
        List<Event> list;
        if (isAdmin()) {
            list = eventRepository.findAll();
        } else {
            list = eventRepository.findByCreatedByOrderByCreatedAtDesc(email);
        }
        return ResponseEntity.ok(list.stream().map(this::toEventDto).collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<?> createEvent(@RequestBody Event event) {
        String email = getLoggedInUserEmail();
        event.setCreatedBy(email);
        event.setCreatedAt(LocalDateTime.now());
        
        if (isAdmin()) {
            event.setStatus("APPROVED");
        } else {
            event.setStatus("PENDING");
        }

        // Auto-calculate system web usage from user's tenant
        boolean usingSystemWeb = false;
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getTenant() != null) {
                usingSystemWeb = user.getTenant().isUsingSystemWeb();
            }
        }
        if (isAdmin()) {
            usingSystemWeb = true;
        }

        event.setIsUsingSystemWeb(usingSystemWeb);
        event.setCommissionRate(usingSystemWeb ? 5.0 : 10.0);

        Event saved = eventRepository.save(event);
        return ResponseEntity.ok(toEventDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateEvent(@PathVariable Long id, @RequestBody Event details) {
        Optional<Event> opt = eventRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Event existing = opt.get();
        String email = getLoggedInUserEmail();
        
        if (!isAdmin() && !existing.getCreatedBy().equals(email)) {
            return ResponseEntity.status(403).body("Access Denied");
        }

        existing.setTitle(details.getTitle());
        existing.setDate(details.getDate());
        existing.setTime(details.getTime());
        existing.setLocation(details.getLocation());
        existing.setRestaurantName(details.getRestaurantName());
        existing.setTag(details.getTag());
        existing.setImageUrl(details.getImageUrl());
        existing.setPrice(details.getPrice());
        existing.setCapacity(details.getCapacity());
        existing.setDescription(details.getDescription());
        existing.setHighlights(details.getHighlights());
        existing.setBranchId(details.getBranchId());
        existing.setEventDates(details.getEventDates());

        // Auto-calculate system web usage from user's tenant
        boolean usingSystemWeb = false;
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getTenant() != null) {
                usingSystemWeb = user.getTenant().isUsingSystemWeb();
            }
        }
        if (isAdmin()) {
            usingSystemWeb = true;
        }

        existing.setIsUsingSystemWeb(usingSystemWeb);
        existing.setCommissionRate(usingSystemWeb ? 5.0 : 10.0);

        if (!isAdmin()) {
            existing.setStatus("PENDING");
        } else if (details.getStatus() != null) {
            existing.setStatus(details.getStatus());
        }

        Event saved = eventRepository.save(existing);
        return ResponseEntity.ok(toEventDto(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEvent(@PathVariable Long id) {
        Optional<Event> opt = eventRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Event existing = opt.get();
        String email = getLoggedInUserEmail();

        if (!isAdmin() && !existing.getCreatedBy().equals(email)) {
            return ResponseEntity.status(403).body("Access Denied");
        }

        eventRepository.delete(existing);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveEvent(@PathVariable Long id) {
        if (!isAdmin()) return ResponseEntity.status(403).body("Access Denied");
        Optional<Event> opt = eventRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Event existing = opt.get();
        existing.setStatus("APPROVED");
        Event saved = eventRepository.save(existing);
        return ResponseEntity.ok(toEventDto(saved));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectEvent(@PathVariable Long id) {
        if (!isAdmin()) return ResponseEntity.status(403).body("Access Denied");
        Optional<Event> opt = eventRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Event existing = opt.get();
        existing.setStatus("REJECTED");
        Event saved = eventRepository.save(existing);
        return ResponseEntity.ok(toEventDto(saved));
    }
}
