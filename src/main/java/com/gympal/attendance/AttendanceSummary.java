package com.gympal.attendance;

import com.gympal.common.enums.AttendanceStatus;
import com.gympal.members.Member;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "attendance_summary", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"member_id", "attendance_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "first_in")
    private LocalTime firstIn;

    @Column(name = "last_out")
    private LocalTime lastOut;

    @Column(name = "total_punches")
    @Builder.Default
    private int totalPunches = 0;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "attendance_status")
    @Builder.Default
    private AttendanceStatus status = AttendanceStatus.present;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "gym_owner_id", nullable = false)
    private UUID gymOwnerId;

    @PrePersist
    protected void onCreate() {
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
