package com.ecommerce.oms.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private UUID id;
    private String name;
    private String description;
    private UUID categoryId;
    private String categoryName;
    private BigDecimal price;
    private boolean active;
    private Instant createdAt;
    private ProductAvailabilitySummary availability;
}
