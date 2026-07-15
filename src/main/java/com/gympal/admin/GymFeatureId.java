package com.gympal.admin;

import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GymFeatureId implements Serializable {
    private UUID gymId;
    private String featureKey;
}
