package com.gympal.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceSummaryRepository extends JpaRepository<AttendanceSummary, Long> {
    Optional<AttendanceSummary> findByIdAndGymOwnerId(Long id, UUID gymOwnerId);
    Optional<AttendanceSummary> findByMemberIdAndAttendanceDate(Long memberId, LocalDate attendanceDate);
    List<AttendanceSummary> findByGymOwnerIdAndAttendanceDate(UUID gymOwnerId, LocalDate attendanceDate);
    List<AttendanceSummary> findByMemberIdAndGymOwnerIdAndAttendanceDateBetween(Long memberId, UUID gymOwnerId, LocalDate startDate, LocalDate endDate);
}
