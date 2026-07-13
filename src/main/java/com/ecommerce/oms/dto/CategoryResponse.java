package com.ecommerce.oms.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {

    private UUID id;
    private String name;
    private UUID parentCategoryId;
    private String parentCategoryName;
    private boolean hasChildren;
}
