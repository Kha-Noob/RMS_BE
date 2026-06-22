package web.restaurant.swp.modules.inventory.service;

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


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {
    private final BranchInventoryRepository branchInventoryRepository;
    private final ProductStockRepository productStockRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final InventoryLogRepository inventoryLogRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final BranchRepository branchRepository;
    private final BranchTransferRepository branchTransferRepository;
    private final BranchTransferItemRepository branchTransferItemRepository;

    // REQ-INV-02: Recipe Stock Deduction on checkout completion / individual served
    @Transactional
    public void deductStockForOrderDetail(OrderDetail detail) {
        if (detail.isDeducted()) return;

        ProductVariant variant = detail.getVariant();
        int quantitySold = detail.getQuantity();
        String branchId = detail.getOrder().getSession().getTable().getRoom().getBranch().getBranchId();
        Branch branch = detail.getOrder().getSession().getTable().getRoom().getBranch();

        // Fetch ingredients mapped for this variant
        List<ProductStock> recipes = productStockRepository.findByVariantId(variant.getId());
        for (ProductStock recipe : recipes) {
            InventoryItem item = recipe.getItem();
            double totalDeduction = recipe.getQuantityNeeded() * quantitySold;

            // Subtract from branch inventory
            Optional<BranchInventory> binvOpt = branchInventoryRepository.findByBranchBranchIdAndItemId(branchId, item.getId());
            if (binvOpt.isPresent()) {
                BranchInventory binv = binvOpt.get();
                binv.setQuantity(Math.max(0.0, binv.getQuantity() - totalDeduction));
                branchInventoryRepository.save(binv);

                // Record log
                InventoryLog invLog = InventoryLog.builder()
                        .branch(branch)
                        .item(item)
                        .changeQuantity(-totalDeduction)
                        .type("OrderDeduction")
                        .reason("Deduction for Order Detail #" + detail.getId() + " (Served)")
                        .logDate(LocalDateTime.now())
                        .build();
                inventoryLogRepository.save(invLog);

                // Check low-stock alerts
                checkLowStockAlert(binv);
            }
        }
        detail.setDeducted(true);
        orderDetailRepository.save(detail);
    }

    @Transactional
    public void deductStockForSession(Long sessionId) {
        List<OrderDetail> details = orderDetailRepository.findByOrderSessionId(sessionId);
        if (details.isEmpty()) return;

        for (OrderDetail detail : details) {
            if (!detail.isDeducted()) {
                deductStockForOrderDetail(detail);
            }
        }
    }


    // REQ-INV-03: Manual Stock adjustments
    @Transactional
    public void recordManualMovement(String branchId, Long itemId, double changeQty, String type, String reason) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chi nhánh."));
        InventoryItem item = inventoryItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nguyên liệu."));

        Optional<BranchInventory> binvOpt = branchInventoryRepository.findByBranchBranchIdAndItemId(branchId, itemId);
        BranchInventory binv;
        if (binvOpt.isPresent()) {
            binv = binvOpt.get();
            binv.setQuantity(Math.max(0.0, binv.getQuantity() + changeQty));
        } else {
            binv = BranchInventory.builder()
                    .branch(branch)
                    .item(item)
                    .quantity(Math.max(0.0, changeQty))
                    .reorderPoint(item.getMinimumThreshold())
                    .build();
        }
        branchInventoryRepository.save(binv);

        InventoryLog invLog = InventoryLog.builder()
                .branch(branch)
                .item(item)
                .changeQuantity(changeQty)
                .type(type)
                .reason(reason)
                .logDate(LocalDateTime.now())
                .build();
        inventoryLogRepository.save(invLog);

        checkLowStockAlert(binv);
    }

    // REQ-INV-04: Check and trigger Low-stock warnings
    public List<BranchInventory> getLowStockItems(String branchId) {
        List<BranchInventory> items = branchInventoryRepository.findByBranchBranchId(branchId);
        List<BranchInventory> lowStock = new ArrayList<>();
        for (BranchInventory b : items) {
            if (b.getQuantity() <= b.getReorderPoint()) {
                lowStock.add(b);
            }
        }
        return lowStock;
    }

    private void checkLowStockAlert(BranchInventory binv) {
        if (binv.getQuantity() <= binv.getReorderPoint()) {
            log.warn("[CẢNH BÁO TỒN KHO THẤP] Chi nhánh {}: Nguyên liệu {} (SKU: {}) sắp hết! Hiện tại: {} {}, Ngưỡng an toàn: {}",
                    binv.getBranch().getName(), binv.getItem().getName(), binv.getItem().getSku(),
                    binv.getQuantity(), binv.getItem().getUnit(), binv.getReorderPoint());
            // Simulated Email trigger
        }
    }

    // REQ-INV-05: Periodic Stocktake adjustment
    @Transactional
    public void executeStocktake(String branchId, Long itemId, double actualQuantity) {
        Optional<BranchInventory> binvOpt = branchInventoryRepository.findByBranchBranchIdAndItemId(branchId, itemId);
        if (binvOpt.isPresent()) {
            BranchInventory binv = binvOpt.get();
            double theoretical = binv.getQuantity();
            double difference = actualQuantity - theoretical;

            binv.setQuantity(actualQuantity);
            branchInventoryRepository.save(binv);

            InventoryLog invLog = InventoryLog.builder()
                    .branch(binv.getBranch())
                    .item(binv.getItem())
                    .changeQuantity(difference)
                    .type("Adjustment")
                    .reason("Periodic Stocktake. Theoretical: " + theoretical + ", Actual: " + actualQuantity)
                    .logDate(LocalDateTime.now())
                    .build();
            inventoryLogRepository.save(invLog);
        }
    }

    // REQ-MB-04 & 05: Internal Stock Transfer Workflow
    @Transactional
    public BranchTransfer createTransferRequest(String sourceBranchId, String targetBranchId, List<Long> itemIds, List<Double> quantities) {
        Branch source = branchRepository.findById(sourceBranchId).orElseThrow(() -> new RuntimeException("Source branch not found"));
        Branch target = branchRepository.findById(targetBranchId).orElseThrow(() -> new RuntimeException("Target branch not found"));

        BranchTransfer transfer = BranchTransfer.builder()
                .sourceBranch(source)
                .targetBranch(target)
                .status("PENDING")
                .requestDate(LocalDateTime.now())
                .build();
        transfer = branchTransferRepository.save(transfer);

        for (int i = 0; i < itemIds.size(); i++) {
            InventoryItem item = inventoryItemRepository.findById(itemIds.get(i)).orElseThrow(() -> new RuntimeException("Item not found"));
            BranchTransferItem transItem = BranchTransferItem.builder()
                    .transfer(transfer)
                    .item(item)
                    .quantity(quantities.get(i))
                    .build();
            branchTransferItemRepository.save(transItem);
        }

        return transfer;
    }

    @Transactional
    public void approveAndExecuteTransfer(Long transferId) {
        BranchTransfer transfer = branchTransferRepository.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer request not found"));
        
        if (!"PENDING".equalsIgnoreCase(transfer.getStatus())) {
            throw new RuntimeException("Transfer is not in PENDING state");
        }

        List<BranchTransferItem> items = branchTransferItemRepository.findByTransferId(transferId);
        
        // Execute deductions from source and additions to target
        for (BranchTransferItem item : items) {
            // Subtract from source
            recordManualMovement(transfer.getSourceBranch().getBranchId(), item.getItem().getId(), -item.getQuantity(), "TransferOut", "Internal Transfer #" + transferId);
            // Add to target
            recordManualMovement(transfer.getTargetBranch().getBranchId(), item.getItem().getId(), item.getQuantity(), "TransferIn", "Internal Transfer #" + transferId);
        }

        transfer.setStatus("RECEIVED");
        transfer.setApproveDate(LocalDateTime.now());
        branchTransferRepository.save(transfer);
    }
}
