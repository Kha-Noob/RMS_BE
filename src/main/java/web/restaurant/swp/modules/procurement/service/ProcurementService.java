package web.restaurant.swp.modules.procurement.service;

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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcurementService {
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final GoodsReceiptItemRepository goodsReceiptItemRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryService inventoryService;

    // REQ-PRO-02: Create Purchase Order
    @Transactional
    public PurchaseOrder createPurchaseOrder(String branchId, Long supplierId, LocalDate deadline, List<Long> itemIds, List<Double> quantities, List<Double> unitPrices) {
        Supplier supplier = supplierRepository.findById(supplierId).orElseThrow(() -> new RuntimeException("Supplier not found"));
        Branch branch = purchaseOrderRepository.findByBranchBranchId(branchId).stream()
                .map(PurchaseOrder::getBranch)
                .findFirst()
                .orElse(Branch.builder().branchId(branchId).build()); // Fallback placeholder if no previous PO

        String poCode = "PO-" + System.currentTimeMillis() / 1000L;

        PurchaseOrder po = PurchaseOrder.builder()
                .poCode(poCode)
                .supplier(supplier)
                .branch(branch)
                .deliveryDeadline(deadline)
                .status("DRAFT")
                .totalAmount(0.0)
                .orderDate(LocalDateTime.now())
                .build();
        po = purchaseOrderRepository.save(po);

        double totalAmount = 0.0;
        for (int i = 0; i < itemIds.size(); i++) {
            InventoryItem item = InventoryItem.builder().id(itemIds.get(i)).build();
            PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                    .purchaseOrder(po)
                    .item(item)
                    .quantity(quantities.get(i))
                    .unitPrice(unitPrices.get(i))
                    .build();
            purchaseOrderItemRepository.save(poItem);
            totalAmount += quantities.get(i) * unitPrices.get(i);
        }

        po.setTotalAmount(totalAmount);
        return purchaseOrderRepository.save(po);
    }

    // REQ-PRO-03: Approve PO and send simulated email PDF
    @Transactional
    public void approvePurchaseOrder(Long poId) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn đặt hàng PO."));
        if (!"DRAFT".equalsIgnoreCase(po.getStatus()) && !"PENDING".equalsIgnoreCase(po.getStatus())) {
            throw new RuntimeException("Đơn hàng không ở trạng thái nháp hoặc chờ duyệt.");
        }

        po.setStatus("SENT");
        purchaseOrderRepository.save(po);

        // Simulation of PDF export & Email dispatch
        log.info("[PROCUREMENT EMAIL SERVICE] Đã tự động kết xuất dữ liệu đơn đặt hàng {} ra file PDF và gửi tới email nhà cung cấp: {}",
                po.getPoCode(), po.getSupplier().getContactEmail());
    }

    // REQ-PRO-04: Goods Receipt Note (GRN)
    @Transactional
    public GoodsReceipt createGoodsReceipt(Long poId, String receivedBy, List<Long> itemIds, List<Double> acceptedQty, List<Double> rejectedQty) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn PO gốc."));

        GoodsReceipt receipt = GoodsReceipt.builder()
                .purchaseOrder(po)
                .receivedDate(LocalDateTime.now())
                .receivedBy(receivedBy)
                .build();
        receipt = goodsReceiptRepository.save(receipt);

        for (int i = 0; i < itemIds.size(); i++) {
            InventoryItem item = InventoryItem.builder().id(itemIds.get(i)).build();
            GoodsReceiptItem receiptItem = GoodsReceiptItem.builder()
                    .goodsReceipt(receipt)
                    .item(item)
                    .quantityReceived(acceptedQty.get(i))
                    .quantityRejected(rejectedQty.get(i))
                    .notes(rejectedQty.get(i) > 0 ? "Hàng lỗi bị trả lại" : "Đạt chất lượng")
                    .build();
            goodsReceiptItemRepository.save(receiptItem);

            // Automatically add accepted quantities into branch inventory stock
            inventoryService.recordManualMovement(
                    po.getBranch().getBranchId(),
                    itemIds.get(i),
                    acceptedQty.get(i),
                    "GRN",
                    "Replenishment from GRN #" + receipt.getId()
            );
        }

        po.setStatus("RECEIVED");
        purchaseOrderRepository.save(po);

        return receipt;
    }

    // REQ-PRO-05: 3-way matching logic
    public boolean performThreeWayMatch(Long poId, double actualInvoiceAmount) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new RuntimeException("PO not found"));
        
        // Find associated Goods Receipt
        List<GoodsReceipt> receipts = goodsReceiptRepository.findByPurchaseOrderBranchBranchId(po.getBranch().getBranchId());
        GoodsReceipt targetReceipt = receipts.stream()
                .filter(r -> r.getPurchaseOrder().getId().equals(poId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No Goods Receipt found for this PO"));

        List<GoodsReceiptItem> receiptItems = goodsReceiptItemRepository.findByGoodsReceiptId(targetReceipt.getId());
        List<PurchaseOrderItem> poItems = purchaseOrderItemRepository.findByPurchaseOrderId(poId);

        // Sum up standard received cost
        double calculatedReceivedValue = 0.0;
        for (GoodsReceiptItem rItem : receiptItems) {
            // Find unit price in PO items
            double unitPrice = poItems.stream()
                    .filter(pi -> pi.getItem().getId().equals(rItem.getItem().getId()))
                    .mapToDouble(PurchaseOrderItem::getUnitPrice)
                    .findFirst()
                    .orElse(0.0);
            calculatedReceivedValue += rItem.getQuantityReceived() * unitPrice;
        }

        // Check if invoice amount deviates by more than 2%
        double deviation = Math.abs(actualInvoiceAmount - calculatedReceivedValue) / calculatedReceivedValue;
        if (deviation > 0.02) {
            log.error("[CẢNH BÁO ĐỐI CHIẾU HÓA ĐƠN] Số tiền trên hóa đơn thực tế lệch quá 2% so với giá trị hàng thực tế nhận! Invoice: {}, GRN Cost: {}",
                    actualInvoiceAmount, calculatedReceivedValue);
            return false; // Blocks payment process
        }
        return true;
    }

    // REQ-PRO-06: Cron Job for delayed POs
    @Scheduled(cron = "0 0 2 * * ?") // Runs daily at 2:00 AM
    public void checkForOverduePurchaseOrders() {
        List<PurchaseOrder> allOrders = purchaseOrderRepository.findAll();
        LocalDate today = LocalDate.now();

        for (PurchaseOrder po : allOrders) {
            if (!"RECEIVED".equalsIgnoreCase(po.getStatus()) && !"CANCELLED".equalsIgnoreCase(po.getStatus())) {
                if (po.getDeliveryDeadline() != null && po.getDeliveryDeadline().isBefore(today)) {
                    log.warn("[CẢNH BÁO GIAO TRỄ] Đơn hàng PO {} nhà cung cấp {} đã trễ hẹn giao! Hạn giao: {}",
                            po.getPoCode(), po.getSupplier().getName(), po.getDeliveryDeadline());
                }
            }
        }
    }
}
