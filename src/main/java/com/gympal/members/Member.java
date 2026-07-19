package com.gympal.members;

import com.gympal.common.enums.AccessStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "mobile_number", nullable = false)
    private String mobileNumber;

    @Column(name = "email")
    private String email;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "address")
    private String address;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "joined_date", nullable = false)
    private LocalDate joinedDate;

    @JsonProperty("isActive")
    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "biometric_uid")
    private String biometricUid;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_status")
    @Builder.Default
    private AccessStatus accessStatus = AccessStatus.blocked;

    @Column(name = "block_reason")
    private String blockReason;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "gym_owner_id", nullable = false)
    private UUID gymOwnerId;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (accessStatus == null) {
            accessStatus = AccessStatus.allowed;
        }
    }
}
