package com.ecommerce.oms.service;

import com.ecommerce.oms.domain.ReturnStatus;
import com.ecommerce.oms.dto.ReturnDecisionRequest;
import com.ecommerce.oms.dto.ReturnRequestCreate;
import com.ecommerce.oms.dto.ReturnRequestResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReturnService {
    ReturnRequestResponse requestReturn(UUID customerId, ReturnRequestCreate request);
    Page<ReturnRequestResponse> getCustomerReturns(UUID customerId, Pageable pageable);
    ReturnRequestResponse getReturnDetail(UUID customerId, UUID returnId);
    Page<ReturnRequestResponse> getReturnsByStatus(ReturnStatus status, Pageable pageable);
    ReturnRequestResponse decideReturn(UUID returnId, ReturnDecisionRequest request, String actor);
}
