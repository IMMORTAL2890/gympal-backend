package com.gympal.attendance;

import com.gympal.common.enums.AttendanceStatus;
import com.gympal.common.enums.PunchSource;
import com.gympal.common.enums.PunchType;
import com.gympal.common.exceptions.NotFoundException;
import com.gympal.members.Member;
import com.gympal.members.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AttendanceService {

    @Autowired
    private AttendanceLogRepository attendanceLogRepository;

    @Autowired
    private AttendanceSummaryRepository attendanceSummaryRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BiometricDeviceRepository biometricDeviceRepository;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Transactional
    public AttendanceLog registerPunch(Long memberId, String biometricUid, Long deviceId, Instant punchTime, PunchType punchType, PunchSource source, String note, UUID gymOwnerId) {
        Member member = memberRepository.findByIdAndGymOwnerId(memberId, gymOwnerId)
                .orElseThrow(() -> new NotFoundException("Member not found: " + memberId));

        BiometricDevice device = null;
        if (deviceId != null) {
            device = biometricDeviceRepository.findByIdAndGymOwnerId(deviceId, gymOwnerId)
                        .orElseThrow(() -> new NotFoundException("Device not found: " + deviceId));
        }

        // Check for duplicate punch (within 60s of another punch by the same member)
        Instant startCheck = punchTime.minus(Duration.ofSeconds(60));
        Instant endCheck = punchTime.plus(Duration.ofSeconds(60));
        List<AttendanceLog> nearPunches = attendanceLogRepository.findByMemberIdAndPunchTimeBetween(memberId, startCheck, endCheck);

        boolean isDuplicate = nearPunches.stream()
                .anyMatch(log -> !log.isDuplicate()); // duplicate if there's any non-duplicate punch in the window

        AttendanceLog log = AttendanceLog.builder()
                .member(member)
                .biometricUid(biometricUid)
                .device(device)
                .punchTime(punchTime)
                .punchType(punchType != null ? punchType : PunchType.unknown)
                .source(source != null ? source : PunchSource.biometric)
                .isDuplicate(isDuplicate)
                .note(note)
                .gymOwnerId(gymOwnerId)
                .build();

        attendanceLogRepository.save(log);

        // Rebuild attendance summary for the punch date (in IST)
        LocalDate punchDate = punchTime.atZone(IST).toLocalDate();
        rebuildAttendanceSummary(memberId, punchDate, gymOwnerId);

        return log;
    }

    @Transactional
    public void rebuildAttendanceSummary(Long memberId, LocalDate date, UUID gymOwnerId) {
        Member member = memberRepository.findByIdAndGymOwnerId(memberId, gymOwnerId)
                .orElseThrow(() -> new NotFoundException("Member not found: " + memberId));

        // Get bounds in UTC for the start and end of the day in IST
        ZonedDateTime startOfDayIST = date.atStartOfDay(IST);
        ZonedDateTime endOfDayIST = date.plusDays(1).atStartOfDay(IST).minusNanos(1);

        Instant startInstant = startOfDayIST.toInstant();
        Instant endInstant = endOfDayIST.toInstant();

        List<AttendanceLog> dayPunches = attendanceLogRepository
                .findByMemberIdAndPunchTimeBetweenOrderByPunchTimeAsc(memberId, startInstant, endInstant);

        // Filter out duplicates
        List<AttendanceLog> validPunches = dayPunches.stream()
                .filter(p -> !p.isDuplicate())
                .toList();

        Optional<AttendanceSummary> existingSummaryOpt = attendanceSummaryRepository
                .findByMemberIdAndAttendanceDate(memberId, date);

        if (validPunches.isEmpty()) {
            // No valid punches, delete summary if it exists (absent members are not stored in summary table)
            existingSummaryOpt.ifPresent(attendanceSummaryRepository::delete);
        } else {
            LocalTime firstIn = validPunches.get(0).getPunchTime().atZone(IST).toLocalTime();
            LocalTime lastOut = validPunches.get(validPunches.size() - 1).getPunchTime().atZone(IST).toLocalTime();
            int totalPunches = validPunches.size();

            AttendanceSummary summary = existingSummaryOpt.orElseGet(() -> AttendanceSummary.builder()
                    .member(member)
                    .attendanceDate(date)
                    .gymOwnerId(gymOwnerId)
                    .build());

            summary.setFirstIn(firstIn);
            summary.setLastOut(lastOut);
            summary.setTotalPunches(totalPunches);
            summary.setStatus(AttendanceStatus.present);
            summary.setUpdatedAt(Instant.now());

            attendanceSummaryRepository.save(summary);
        }
    }
}
