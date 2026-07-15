package com.gympal.gym;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gym_owner")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GymOwner {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "auth_user_id", unique = true, nullable = false)
    private UUID authUserId;

    @Column(name = "gym_name", nullable = false)
    private String gymName;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(name = "mobile_number", nullable = false)
    private String mobileNumber;

    @Column(name = "auto_reminder_enabled")
    @Builder.Default
    private boolean autoReminderEnabled = true;

    @Column(name = "reminder_days_before")
    @Builder.Default
    private int reminderDaysBefore = 3;

    @Column(name = "status")
    @Builder.Default
    private String status = "active";

    @Column(name = "subscription_plan")
    @Builder.Default
    private String subscriptionPlan = "BASIC";

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
