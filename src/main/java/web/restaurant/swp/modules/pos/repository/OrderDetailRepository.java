package web.restaurant.swp.modules.pos.repository;

import web.restaurant.swp.modules.auth.model.*;
import web.restaurant.swp.modules.auth.repository.*;
import web.restaurant.swp.modules.auth.service.*;
import web.restaurant.swp.modules.pos.model.*;
import web.restaurant.swp.modules.pos.repository.*;
import web.restaurant.swp.modules.pos.service.*;
import web.restaurant.swp.modules.inventory.model.*;
import web.restaurant.swp.modules.inventory.repository.*;
import web.restaurant.swp.modules.inventory.service.*;
import web.restaurant.swp.modules.procurement.model.*;
import web.restaurant.swp.modules.procurement.repository.*;
import web.restaurant.swp.modules.procurement.service.*;
import web.restaurant.swp.modules.hr.model.*;
import web.restaurant.swp.modules.hr.repository.*;
import web.restaurant.swp.modules.hr.service.*;
import web.restaurant.swp.modules.loyalty.model.*;
import web.restaurant.swp.modules.loyalty.repository.*;
import web.restaurant.swp.modules.loyalty.service.*;
import web.restaurant.swp.modules.promotion.model.*;
import web.restaurant.swp.modules.promotion.repository.*;
import web.restaurant.swp.modules.promotion.service.*;
import web.restaurant.swp.modules.analytics.service.*;
import web.restaurant.swp.modules.branch.model.*;
import web.restaurant.swp.modules.branch.repository.*;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {
    List<OrderDetail> findByOrderId(Long orderId);
    List<OrderDetail> findByOrderSessionId(Long sessionId);

    @Query("SELECT od.variant.product.name, SUM(od.quantity) " +
           "FROM OrderDetail od " +
           "WHERE od.order.status = 'SERVED' AND od.order.branchId = :branchId " +
           "GROUP BY od.variant.product.name " +
           "ORDER BY SUM(od.quantity) DESC")
    List<Object[]> findBestSellersByBranch(@org.springframework.data.repository.query.Param("branchId") String branchId);

    @Query("SELECT od.variant.product.name, SUM(od.quantity) " +
           "FROM OrderDetail od " +
           "WHERE od.order.status = 'SERVED' " +
           "GROUP BY od.variant.product.name " +
           "ORDER BY SUM(od.quantity) DESC")
    List<Object[]> findBestSellersAll();
}

// --- INVENTORY ---
