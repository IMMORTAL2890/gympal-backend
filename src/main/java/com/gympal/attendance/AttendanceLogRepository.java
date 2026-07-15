package com.gympal.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {
    Optional<AttendanceLog> findByIdAndGymOwnerId(Long id, UUID gymOwnerId);
    List<AttendanceLog> findByMemberIdAndGymOwnerId(Long memberId, UUID gymOwnerId);
    List<AttendanceLog> findByGymOwnerId(UUID gymOwnerId);

    @Query("SELECT al FROM AttendanceLog al WHERE al.member.id = :memberId AND al.punchTime BETWEEN :start AND :end")
    List<AttendanceLog> findByMemberIdAndPunchTimeBetween(@Param("memberId") Long memberId, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT al FROM AttendanceLog al WHERE al.member.id = :memberId AND al.punchTime BETWEEN :start AND :end ORDER BY al.punchTime ASC")
    List<AttendanceLog> findByMemberIdAndPunchTimeBetweenOrderByPunchTimeAsc(@Param("memberId") Long memberId, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT al FROM AttendanceLog al WHERE al.member.id = :memberId ORDER BY al.punchTime DESC")
    List<AttendanceLog> findLatestPunchByMemberId(@Param("memberId") Long memberId);
}
