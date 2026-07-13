package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.AuditLog;
import com.ecommerce.oms.domain.InventoryItem;
import com.ecommerce.oms.domain.Product;
import com.ecommerce.oms.domain.Warehouse;
import com.ecommerce.oms.dto.InventoryItemResponse;
import com.ecommerce.oms.dto.ProductAvailabilitySummary;
import com.ecommerce.oms.exception.InvalidInventoryOperationException;
import com.ecommerce.oms.repository.AuditLogRepository;
import com.ecommerce.oms.repository.InventoryItemRepository;
import com.ecommerce.oms.repository.ProductRepository;
import com.ecommerce.oms.repository.WarehouseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private InventoryService inventoryService;

    private UUID productId;
    private UUID warehouseId;
    private Product product;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();

        product = Product.builder()
                .id(productId)
                .name("Test Product")
                .active(true)
                .build();

        warehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Test WH")
                .location("Test Location")
                .build();
    }

    @Test
    void testSetStockSuccess() {
        InventoryItem item = InventoryItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .warehouse(warehouse)
                .quantityOnHand(5)
                .quantityReserved(2)
                .build();

        when(inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(i -> i.getArguments()[0]);

        InventoryItemResponse response = inventoryService.setStock(productId, warehouseId, 10, "admin@test.com");

        assertNotNull(response);
        assertEquals(10, response.getQuantityOnHand());
        assertEquals(2, response.getQuantityReserved());
        assertEquals(8, response.getQuantityAvailable());

        verify(inventoryItemRepository, times(1)).save(item);
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    void testSetStockNegativeQuantityFails() {
        assertThrows(InvalidInventoryOperationException.class, () ->
                inventoryService.setStock(productId, warehouseId, -1, "admin@test.com"));
    }

    @Test
    void testSetStockLessThanReservedFails() {
        InventoryItem item = InventoryItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .warehouse(warehouse)
                .quantityOnHand(5)
                .quantityReserved(4)
                .build();

        when(inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)).thenReturn(Optional.of(item));

        assertThrows(InvalidInventoryOperationException.class, () ->
                inventoryService.setStock(productId, warehouseId, 3, "admin@test.com"));
    }

    @Test
    void testAdjustStockPositiveDeltaSuccess() {
        InventoryItem item = InventoryItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .warehouse(warehouse)
                .quantityOnHand(5)
                .quantityReserved(2)
                .build();

        when(inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(i -> i.getArguments()[0]);

        InventoryItemResponse response = inventoryService.adjustStock(productId, warehouseId, 3, "Add stock", "admin@test.com");

        assertNotNull(response);
        assertEquals(8, response.getQuantityOnHand());
        assertEquals(6, response.getQuantityAvailable());

        verify(inventoryItemRepository, times(1)).save(item);
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    void testAdjustStockNegativeDeltaSuccess() {
        InventoryItem item = InventoryItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .warehouse(warehouse)
                .quantityOnHand(5)
                .quantityReserved(2)
                .build();

        when(inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(i -> i.getArguments()[0]);

        InventoryItemResponse response = inventoryService.adjustStock(productId, warehouseId, -2, "Reduce stock", "admin@test.com");

        assertNotNull(response);
        assertEquals(3, response.getQuantityOnHand());
        assertEquals(1, response.getQuantityAvailable());

        verify(inventoryItemRepository, times(1)).save(item);
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    void testAdjustStockNegativeResultFails() {
        InventoryItem item = InventoryItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .warehouse(warehouse)
                .quantityOnHand(5)
                .quantityReserved(2)
                .build();

        when(inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)).thenReturn(Optional.of(item));

        assertThrows(InvalidInventoryOperationException.class, () ->
                inventoryService.adjustStock(productId, warehouseId, -6, "Reduce stock", "admin@test.com"));
    }

    @Test
    void testAdjustStockLessThanReservedFails() {
        InventoryItem item = InventoryItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .warehouse(warehouse)
                .quantityOnHand(5)
                .quantityReserved(3)
                .build();

        when(inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)).thenReturn(Optional.of(item));

        assertThrows(InvalidInventoryOperationException.class, () ->
                inventoryService.adjustStock(productId, warehouseId, -3, "Reduce stock", "admin@test.com"));
    }

    @Test
    void testGetAvailabilitySummary() {
        InventoryItem item1 = InventoryItem.builder()
                .product(product)
                .warehouse(warehouse)
                .quantityOnHand(10)
                .quantityReserved(3)
                .build();

        Warehouse wh2 = Warehouse.builder().id(UUID.randomUUID()).name("WH2").location("LA").build();
        InventoryItem item2 = InventoryItem.builder()
                .product(product)
                .warehouse(wh2)
                .quantityOnHand(5)
                .quantityReserved(0)
                .build();

        when(productRepository.existsById(productId)).thenReturn(true);
        when(inventoryItemRepository.findByProductId(productId)).thenReturn(Arrays.asList(item1, item2));

        ProductAvailabilitySummary summary = inventoryService.getAvailabilitySummary(productId);

        assertNotNull(summary);
        assertEquals(productId, summary.getProductId());
        assertTrue(summary.isAvailable());
        assertEquals(12, summary.getTotalAvailableQuantity()); // (10-3) + (5-0) = 7 + 5 = 12
    }

    @Test
    void testGetAvailabilitySummaryZeroStock() {
        when(productRepository.existsById(productId)).thenReturn(true);
        when(inventoryItemRepository.findByProductId(productId)).thenReturn(Collections.emptyList());

        ProductAvailabilitySummary summary = inventoryService.getAvailabilitySummary(productId);

        assertNotNull(summary);
        assertEquals(productId, summary.getProductId());
        assertFalse(summary.isAvailable());
        assertEquals(0, summary.getTotalAvailableQuantity());
    }
}
