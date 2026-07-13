package com.ecommerce.oms.dto;

import com.ecommerce.oms.domain.UserRole;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSummary {
    private UUID id;
    private String name;
    private String email;
    private UserRole role;
}
