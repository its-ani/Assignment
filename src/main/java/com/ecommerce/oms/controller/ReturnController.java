package com.ecommerce.oms.controller;

import com.ecommerce.oms.domain.ReturnStatus;
import com.ecommerce.oms.dto.ReturnDecisionRequest;
import com.ecommerce.oms.dto.ReturnRequestCreate;
import com.ecommerce.oms.dto.ReturnRequestResponse;
import com.ecommerce.oms.security.SecurityUtils;
import com.ecommerce.oms.service.ReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/returns")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService returnService;

    private UUID getAuthenticatedCustomerId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Access denied: User is not authenticated"));
    }

    private String getAuthenticatedUserEmail() {
        return SecurityUtils.getCurrentUserEmail()
                .orElse("UNKNOWN");
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ReturnRequestResponse> createReturn(
            @Valid @RequestBody ReturnRequestCreate request) {
        UUID customerId = getAuthenticatedCustomerId();
        ReturnRequestResponse response = returnService.requestReturn(customerId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Page<ReturnRequestResponse>> getMyReturns(Pageable pageable) {
        UUID customerId = getAuthenticatedCustomerId();
        Page<ReturnRequestResponse> response = returnService.getCustomerReturns(customerId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ReturnRequestResponse> getMyReturnDetail(@PathVariable UUID id) {
        UUID customerId = getAuthenticatedCustomerId();
        ReturnRequestResponse response = returnService.getReturnDetail(customerId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/staff")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_STAFF')")
    public ResponseEntity<Page<ReturnRequestResponse>> getReturnsForStaff(
            @RequestParam(required = false) ReturnStatus status,
            Pageable pageable) {
        Page<ReturnRequestResponse> response = returnService.getReturnsByStatus(status, pageable);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/decision")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_STAFF')")
    public ResponseEntity<ReturnRequestResponse> decideReturn(
            @PathVariable UUID id,
            @Valid @RequestBody ReturnDecisionRequest request) {
        String actor = getAuthenticatedUserEmail();
        ReturnRequestResponse response = returnService.decideReturn(id, request, actor);
        return ResponseEntity.ok(response);
    }
}
