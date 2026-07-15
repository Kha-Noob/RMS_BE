package web.restaurant.swp.modules.event.repository;

import web.restaurant.swp.modules.event.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByStatusOrderByCreatedAtDesc(String status);
    List<Event> findByCreatedByOrderByCreatedAtDesc(String createdBy);
}
