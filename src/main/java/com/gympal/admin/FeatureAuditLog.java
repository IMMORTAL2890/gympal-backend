package com.gympal.admin;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feature_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "admin_id")
    private UUID adminId;

    @Column(name = "gym_id", nullable = false)
    private UUID gymId;

    @Column(name = "feature_key", nullable = false)
    private String featureKey;

    @Column(name = "old_value")
    private Boolean oldValue;

    @Column(name = "new_value", nullable = false)
    private Boolean newValue;

    @Column(name = "timestamp")
    @Builder.Default
    private Instant timestamp = Instant.now();

    @PrePersist
    protected void onCreate() {
        timestamp = Instant.now();
    }
}
