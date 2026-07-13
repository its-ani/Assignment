package com.ecommerce.oms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnDecisionRequest {
    @NotNull(message = "Approved decision cannot be null")
    private Boolean approved;
    
    private String rejectionReason;
}
