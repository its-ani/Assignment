package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.AuditLog;
import com.ecommerce.oms.domain.InventoryItem;
import com.ecommerce.oms.domain.Product;
import com.ecommerce.oms.domain.Warehouse;
import com.ecommerce.oms.dto.InventoryItemResponse;
import com.ecommerce.oms.dto.ProductAvailabilitySummary;
import com.ecommerce.oms.exception.InvalidInventoryOperationException;
import com.ecommerce.oms.exception.ResourceNotFoundException;
import com.ecommerce.oms.repository.AuditLogRepository;
import com.ecommerce.oms.repository.InventoryItemRepository;
import com.ecommerce.oms.repository.ProductRepository;
import com.ecommerce.oms.repository.WarehouseRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public InventoryService(
            InventoryItemRepository inventoryItemRepository,
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository,
            AuditLogRepository auditLogRepository,
            ObjectMapper objectMapper
    ) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InventoryItem getOrCreateInventoryItem(UUID productId, UUID warehouseId) {
        return inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseGet(() -> {
                    Product product = productRepository.findById(productId)
                            .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));
                    Warehouse warehouse = warehouseRepository.findById(warehouseId)
                            .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with ID: " + warehouseId));

                    InventoryItem item = InventoryItem.builder()
                            .product(product)
                            .warehouse(warehouse)
                            .quantityOnHand(0)
                            .quantityReserved(0)
                            .build();
                    return inventoryItemRepository.save(item);
                });
    }

    @Transactional
    public InventoryItemResponse setStock(UUID productId, UUID warehouseId, int quantityOnHand, String actor) {
        if (quantityOnHand < 0) {
            throw new InvalidInventoryOperationException("Quantity on hand cannot be negative.");
        }

        InventoryItem item = getOrCreateInventoryItem(productId, warehouseId);
        int beforeOnHand = item.getQuantityOnHand();
        int beforeReserved = item.getQuantityReserved();

        if (item.getQuantityReserved() > quantityOnHand) {
            throw new InvalidInventoryOperationException(
                    String.format("Cannot set quantity on hand to %d because it is less than the reserved quantity of %d.",
                            quantityOnHand, item.getQuantityReserved())
            );
        }

        item.setQuantityOnHand(quantityOnHand);
        InventoryItem saved = inventoryItemRepository.save(item);

        writeAuditLog(saved.getId(), "SET_STOCK", actor, beforeOnHand, quantityOnHand, beforeReserved, beforeReserved, "Absolute stock set");

        return mapToResponse(saved);
    }

    @Transactional
    public InventoryItemResponse adjustStock(UUID productId, UUID warehouseId, int delta, String reason, String actor) {
        InventoryItem item = getOrCreateInventoryItem(productId, warehouseId);
        int beforeOnHand = item.getQuantityOnHand();
        int beforeReserved = item.getQuantityReserved();

        int newQuantityOnHand = beforeOnHand + delta;
        if (newQuantityOnHand < 0) {
            throw new InvalidInventoryOperationException(
                    String.format("Adjustment by %d would make quantity on hand negative (current: %d).", delta, beforeOnHand)
            );
        }

        if (item.getQuantityReserved() > newQuantityOnHand) {
            throw new InvalidInventoryOperationException(
                    String.format("Adjustment by %d would make quantity on hand (%d) less than the reserved quantity of %d.",
                            delta, newQuantityOnHand, item.getQuantityReserved())
            );
        }

        item.setQuantityOnHand(newQuantityOnHand);
        InventoryItem saved = inventoryItemRepository.save(item);

        writeAuditLog(saved.getId(), "ADJUST_STOCK", actor, beforeOnHand, newQuantityOnHand, beforeReserved, beforeReserved, reason);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> getInventoryForProduct(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found with ID: " + productId);
        }
        return inventoryItemRepository.findByProductId(productId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> getInventoryForWarehouse(UUID warehouseId) {
        if (!warehouseRepository.existsById(warehouseId)) {
            throw new ResourceNotFoundException("Warehouse not found with ID: " + warehouseId);
        }
        return inventoryItemRepository.findByWarehouseId(warehouseId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductAvailabilitySummary getAvailabilitySummary(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found with ID: " + productId);
        }
        List<InventoryItem> items = inventoryItemRepository.findByProductId(productId);
        int totalAvailable = items.stream()
                .mapToInt(item -> item.getQuantityOnHand() - item.getQuantityReserved())
                .sum();

        return ProductAvailabilitySummary.builder()
                .productId(productId)
                .available(totalAvailable > 0)
                .totalAvailableQuantity(totalAvailable)
                .build();
    }

    private void writeAuditLog(
            UUID entityId,
            String action,
            String actor,
            int beforeOnHand,
            int afterOnHand,
            int beforeReserved,
            int afterReserved,
            String reason
    ) {
        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("beforeOnHand", beforeOnHand);
        metadataMap.put("afterOnHand", afterOnHand);
        metadataMap.put("beforeReserved", beforeReserved);
        metadataMap.put("afterReserved", afterReserved);
        metadataMap.put("reason", reason != null ? reason : "");

        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(metadataMap);
        } catch (JsonProcessingException e) {
            metadataJson = "{}";
        }

        AuditLog log = AuditLog.builder()
                .entityType("InventoryItem")
                .entityId(entityId)
                .action(action)
                .actor(actor != null ? actor : "SYSTEM")
                .metadata(metadataJson)
                .build();

        auditLogRepository.save(log);
    }

    private InventoryItemResponse mapToResponse(InventoryItem item) {
        return InventoryItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .warehouseId(item.getWarehouse().getId())
                .warehouseName(item.getWarehouse().getName())
                .quantityOnHand(item.getQuantityOnHand())
                .quantityReserved(item.getQuantityReserved())
                .quantityAvailable(item.getQuantityOnHand() - item.getQuantityReserved())
                .build();
    }
}
