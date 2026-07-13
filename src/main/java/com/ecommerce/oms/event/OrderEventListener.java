package com.ecommerce.oms.event;

import com.ecommerce.oms.domain.AuditLog;
import com.ecommerce.oms.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final AuditLogRepository auditLogRepository;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("Processing OrderPlacedEvent for order: {}", event.getOrderId());
        
        AuditLog auditLog = AuditLog.builder()
                .entityType("ORDER")
                .entityId(event.getOrderId())
                .action("ORDER_PLACED")
                .actor("CUSTOMER_" + event.getCustomerId())
                .timestamp(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                .metadata("{\"totalAmount\":" + event.getTotalAmount() + "}")
                .build();
        
        auditLogRepository.save(auditLog);
        log.info("Successfully created audit log for placed order: {}", event.getOrderId());
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("Processing OrderStatusChangedEvent for order: {}. Old status: {}, New status: {}, Actor: {}", 
                event.getOrderId(), event.getOldStatus(), event.getNewStatus(), event.getActor());
        
        AuditLog auditLog = AuditLog.builder()
                .entityType("ORDER")
                .entityId(event.getOrderId())
                .action("ORDER_STATUS_CHANGED")
                .actor(event.getActor())
                .timestamp(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                .metadata(String.format("{\"oldStatus\":\"%s\",\"newStatus\":\"%s\"}", event.getOldStatus(), event.getNewStatus()))
                .build();
        
        auditLogRepository.save(auditLog);
        log.info("Successfully created audit log for status change on order: {}", event.getOrderId());
    }
}
