package com.gympal.admin;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gym_features")
@IdClass(GymFeatureId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymFeature {

    @Id
    @Column(name = "gym_id")
    private UUID gymId;

    @Id
    @Column(name = "feature_key")
    private String featureKey;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    @PrePersist
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
