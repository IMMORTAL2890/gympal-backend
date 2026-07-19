package com.gympal.access;

import com.gympal.common.enums.AccessAction;
import com.gympal.attendance.BiometricDevice;
import com.gympal.members.Member;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "access_control_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessControlLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private BiometricDevice device;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private AccessAction action;

    private String reason;

    @Column(name = "performed_by")
    @Builder.Default
    private String performedBy = "owner";

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "gym_owner_id", nullable = false)
    private UUID gymOwnerId;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (performedBy == null) {
            performedBy = "owner";
        }
    }
}
