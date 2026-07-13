package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.Warehouse;
import com.ecommerce.oms.dto.WarehouseRequest;
import com.ecommerce.oms.dto.WarehouseResponse;
import com.ecommerce.oms.exception.ResourceNotFoundException;
import com.ecommerce.oms.exception.WarehouseHasInventoryException;
import com.ecommerce.oms.repository.InventoryItemRepository;
import com.ecommerce.oms.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @InjectMocks
    private WarehouseService warehouseService;

    @Test
    void testCreateWarehouse() {
        WarehouseRequest request = new WarehouseRequest("Main WH", "NYC");
        Warehouse savedWarehouse = new Warehouse(UUID.randomUUID(), "Main WH", "NYC");

        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(savedWarehouse);

        WarehouseResponse response = warehouseService.createWarehouse(request);

        assertNotNull(response);
        assertEquals(savedWarehouse.getId(), response.getId());
        assertEquals("Main WH", response.getName());
        assertEquals("NYC", response.getLocation());
        verify(warehouseRepository, times(1)).save(any(Warehouse.class));
    }

    @Test
    void testUpdateWarehouse() {
        UUID id = UUID.randomUUID();
        WarehouseRequest request = new WarehouseRequest("Updated WH", "LA");
        Warehouse existing = new Warehouse(id, "Main WH", "NYC");
        Warehouse updated = new Warehouse(id, "Updated WH", "LA");

        when(warehouseRepository.findById(id)).thenReturn(Optional.of(existing));
        when(warehouseRepository.save(any(Warehouse.class))).thenReturn(updated);

        WarehouseResponse response = warehouseService.updateWarehouse(id, request);

        assertNotNull(response);
        assertEquals("Updated WH", response.getName());
        assertEquals("LA", response.getLocation());
        verify(warehouseRepository, times(1)).save(existing);
    }

    @Test
    void testUpdateWarehouseNotFound() {
        UUID id = UUID.randomUUID();
        WarehouseRequest request = new WarehouseRequest("Updated WH", "LA");

        when(warehouseRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> warehouseService.updateWarehouse(id, request));
    }

    @Test
    void testDeleteWarehouseNoInventory() {
        UUID id = UUID.randomUUID();
        Warehouse existing = new Warehouse(id, "Main WH", "NYC");

        when(warehouseRepository.findById(id)).thenReturn(Optional.of(existing));
        when(inventoryItemRepository.existsByWarehouseId(id)).thenReturn(false);

        assertDoesNotThrow(() -> warehouseService.deleteWarehouse(id));

        verify(warehouseRepository, times(1)).delete(existing);
    }

    @Test
    void testDeleteWarehouseHasInventory() {
        UUID id = UUID.randomUUID();
        Warehouse existing = new Warehouse(id, "Main WH", "NYC");

        when(warehouseRepository.findById(id)).thenReturn(Optional.of(existing));
        when(inventoryItemRepository.existsByWarehouseId(id)).thenReturn(true);

        assertThrows(WarehouseHasInventoryException.class, () -> warehouseService.deleteWarehouse(id));
        verify(warehouseRepository, never()).delete(any(Warehouse.class));
    }

    @Test
    void testGetWarehouseById() {
        UUID id = UUID.randomUUID();
        Warehouse warehouse = new Warehouse(id, "Main WH", "NYC");

        when(warehouseRepository.findById(id)).thenReturn(Optional.of(warehouse));

        WarehouseResponse response = warehouseService.getWarehouseById(id);

        assertNotNull(response);
        assertEquals(id, response.getId());
        assertEquals("Main WH", response.getName());
    }

    @Test
    void testListWarehouses() {
        Pageable pageable = PageRequest.of(0, 10);
        Warehouse warehouse = new Warehouse(UUID.randomUUID(), "Main WH", "NYC");
        Page<Warehouse> page = new PageImpl<>(Collections.singletonList(warehouse));

        when(warehouseRepository.findAll(pageable)).thenReturn(page);

        Page<WarehouseResponse> responses = warehouseService.listWarehouses(pageable);

        assertNotNull(responses);
        assertEquals(1, responses.getContent().size());
        assertEquals("Main WH", responses.getContent().get(0).getName());
    }
}
