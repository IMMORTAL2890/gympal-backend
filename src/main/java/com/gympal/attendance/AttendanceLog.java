package com.gympal.attendance;

import com.gympal.common.enums.PunchSource;
import com.gympal.common.enums.PunchType;
import com.gympal.members.Member;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendance_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "biometric_uid")
    private String biometricUid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private BiometricDevice device;

    @Column(name = "punch_time", nullable = false)
    private Instant punchTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "punch_type")
    @Builder.Default
    private PunchType punchType = PunchType.unknown;

    @Column(name = "verify_mode")
    @Builder.Default
    private int verifyMode = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    @Builder.Default
    private PunchSource source = PunchSource.biometric;

    @Column(name = "is_duplicate")
    @Builder.Default
    private boolean isDuplicate = false;

    private String note;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "gym_owner_id", nullable = false)
    private UUID gymOwnerId;

    @PrePersist
    protected void onCreate() {
        syncedAt = Instant.now();
    }
}
