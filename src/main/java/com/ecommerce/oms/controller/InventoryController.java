package com.ecommerce.oms.controller;

import com.ecommerce.oms.dto.InventoryAdjustRequest;
import com.ecommerce.oms.dto.InventoryItemResponse;
import com.ecommerce.oms.dto.InventorySetRequest;
import com.ecommerce.oms.security.SecurityUtils;
import com.ecommerce.oms.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PutMapping("/{productId}/warehouse/{warehouseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InventoryItemResponse> setStock(
            @PathVariable UUID productId,
            @PathVariable UUID warehouseId,
            @Valid @RequestBody InventorySetRequest request
    ) {
        String actor = SecurityUtils.getCurrentUserEmail().orElse("SYSTEM");
        InventoryItemResponse response = inventoryService.setStock(productId, warehouseId, request.getQuantityOnHand(), actor);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{productId}/warehouse/{warehouseId}/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InventoryItemResponse> adjustStock(
            @PathVariable UUID productId,
            @PathVariable UUID warehouseId,
            @Valid @RequestBody InventoryAdjustRequest request
    ) {
        String actor = SecurityUtils.getCurrentUserEmail().orElse("SYSTEM");
        InventoryItemResponse response = inventoryService.adjustStock(
                productId,
                warehouseId,
                request.getDelta(),
                request.getReason(),
                actor
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/product/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_STAFF')")
    public ResponseEntity<List<InventoryItemResponse>> getInventoryForProduct(@PathVariable UUID productId) {
        List<InventoryItemResponse> response = inventoryService.getInventoryForProduct(productId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/warehouse/{warehouseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_STAFF')")
    public ResponseEntity<List<InventoryItemResponse>> getInventoryForWarehouse(@PathVariable UUID warehouseId) {
        List<InventoryItemResponse> response = inventoryService.getInventoryForWarehouse(warehouseId);
        return ResponseEntity.ok(response);
    }
}
