package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.Warehouse;
import com.ecommerce.oms.dto.WarehouseRequest;
import com.ecommerce.oms.dto.WarehouseResponse;
import com.ecommerce.oms.exception.ResourceNotFoundException;
import com.ecommerce.oms.exception.WarehouseHasInventoryException;
import com.ecommerce.oms.repository.InventoryItemRepository;
import com.ecommerce.oms.repository.WarehouseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final InventoryItemRepository inventoryItemRepository;

    public WarehouseService(WarehouseRepository warehouseRepository, InventoryItemRepository inventoryItemRepository) {
        this.warehouseRepository = warehouseRepository;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    @Transactional
    public WarehouseResponse createWarehouse(WarehouseRequest request) {
        Warehouse warehouse = Warehouse.builder()
                .name(request.getName())
                .location(request.getLocation())
                .build();
        Warehouse saved = warehouseRepository.save(warehouse);
        return mapToResponse(saved);
    }

    @Transactional
    public WarehouseResponse updateWarehouse(UUID id, WarehouseRequest request) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with ID: " + id));
        warehouse.setName(request.getName());
        warehouse.setLocation(request.getLocation());
        Warehouse updated = warehouseRepository.save(warehouse);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteWarehouse(UUID id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with ID: " + id));
        
        if (inventoryItemRepository.existsByWarehouseId(id)) {
            throw new WarehouseHasInventoryException("Warehouse cannot be deleted because it has associated inventory items.");
        }
        
        warehouseRepository.delete(warehouse);
    }

    @Transactional(readOnly = true)
    public WarehouseResponse getWarehouseById(UUID id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with ID: " + id));
        return mapToResponse(warehouse);
    }

    @Transactional(readOnly = true)
    public Page<WarehouseResponse> listWarehouses(Pageable pageable) {
        return warehouseRepository.findAll(pageable).map(this::mapToResponse);
    }

    private WarehouseResponse mapToResponse(Warehouse warehouse) {
        return WarehouseResponse.builder()
                .id(warehouse.getId())
                .name(warehouse.getName())
                .location(warehouse.getLocation())
                .build();
    }
}
