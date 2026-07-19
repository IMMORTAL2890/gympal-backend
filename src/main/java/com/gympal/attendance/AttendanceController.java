package com.gympal.attendance;

import com.gympal.common.GymOwnerContext;
import com.gympal.common.enums.AttendanceStatus;
import com.gympal.common.enums.PunchSource;
import com.gympal.common.enums.PunchType;
import com.gympal.common.exceptions.UnauthorizedException;
import com.gympal.common.exceptions.BadRequestException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gympal.members.Member;
import com.gympal.members.MemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceSummaryRepository attendanceSummaryRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BiometricDeviceRepository biometricDeviceRepository;

    @Autowired
    private GymOwnerContext gymOwnerContext;

    @Value("${jwt.secret}")
    private String webhookSecret;

    @GetMapping
    public ResponseEntity<List<AttendanceRow>> getAttendance(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        LocalDate checkDate = date != null ? date : LocalDate.now();
        UUID ownerId = gymOwnerContext.getGymOwnerId();

        List<Member> members = memberRepository.findByGymOwnerId(ownerId);
        List<AttendanceSummary> summaries = attendanceSummaryRepository.findByGymOwnerIdAndAttendanceDate(ownerId, checkDate);

        Map<Long, AttendanceSummary> summaryMap = summaries.stream()
                .collect(Collectors.toMap(s -> s.getMember().getId(), s -> s));

        List<AttendanceRow> rows = members.stream().map(m -> {
            AttendanceSummary summary = summaryMap.get(m.getId());
            if (summary != null) {
                return new AttendanceRow(
                        m.getId(),
                        m.getFullName(),
                        m.getBiometricUid(),
                        m.getMobileNumber(),
                        summary.getFirstIn(),
                        summary.getLastOut(),
                        summary.getTotalPunches(),
                        "present"
                );
            } else {
                return new AttendanceRow(
                        m.getId(),
                        m.getFullName(),
                        m.getBiometricUid(),
                        m.getMobileNumber(),
                        null,
                        null,
                        0,
                        "absent"
                );
            }
        })
        .sorted(Comparator.comparing(AttendanceRow::getMemberName))
        .collect(Collectors.toList());

        return ResponseEntity.ok(rows);
    }

    @PostMapping("/punches")
    public ResponseEntity<PunchResponse> receivePunches(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret,
            @RequestBody List<PunchDto> punches) {
        
        // Simple signature check: match webhook secret or a dev secret
        if (secret == null || !secret.equals(webhookSecret)) {
            throw new UnauthorizedException("Unauthorized biometric bridge access");
        }

        int accepted = 0;
        int deduped = 0;

        for (PunchDto p : punches) {
            // Find biometric device by serial
            Optional<BiometricDevice> deviceOpt = biometricDeviceRepository.findAll().stream()
                    .filter(d -> p.getDeviceSerial().equals(d.getDeviceSerial()))
                    .findFirst();

            if (deviceOpt.isPresent()) {
                BiometricDevice device = deviceOpt.get();
                UUID gymOwnerId = device.getGymOwnerId();

                // Find member by biometricUid in this gym
                Optional<Member> memberOpt = memberRepository.findByGymOwnerIdAndBiometricUid(gymOwnerId, p.getBiometricUid());
                if (memberOpt.isPresent()) {
                    Member member = memberOpt.get();
                    try {
                        Instant punchTime = Instant.parse(p.getPunchTime());
                        
                        PunchType ptType = PunchType.unknown;
                        if ("in".equalsIgnoreCase(p.getPunchType())) ptType = PunchType.in;
                        else if ("out".equalsIgnoreCase(p.getPunchType())) ptType = PunchType.out;

                        AttendanceLog log = attendanceService.registerPunch(
                                member.getId(),
                                p.getBiometricUid(),
                                device.getId(),
                                punchTime,
                                ptType,
                                PunchSource.biometric,
                                "Bridge sync",
                                gymOwnerId
                        );

                        if (log.isDuplicate()) {
                            deduped++;
                        } else {
                            accepted++;
                        }
                    } catch (java.time.format.DateTimeParseException e) {
                        // Skip malformed punch timestamp from device — log and continue
                        org.slf4j.LoggerFactory.getLogger(AttendanceController.class)
                                .warn("[PUNCH] Skipping malformed punchTime '{}' from device {}: {}",
                                        p.getPunchTime(), p.getDeviceSerial(), e.getMessage(), e);
                    } catch (Exception e) {
                        // Log and continue processing remaining punches
                        org.slf4j.LoggerFactory.getLogger(AttendanceController.class)
                                .warn("[PUNCH] Failed to register punch for member {} on device {}: {}",
                                        p.getBiometricUid(), p.getDeviceSerial(), e.getMessage(), e);
                    }
                }
            }
        }

        return ResponseEntity.ok(new PunchResponse(accepted, deduped));
    }

    @PostMapping("/manual")
    public ResponseEntity<AttendanceLog> markManualAttendance(@RequestBody ManualPunchRequest request) {
        UUID ownerId = gymOwnerContext.getGymOwnerId();
        
        Member member = memberRepository.findByIdAndGymOwnerId(request.getMemberId(), ownerId)
                .orElseThrow(() -> new com.gympal.common.exceptions.NotFoundException("Member not found: " + request.getMemberId()));

        PunchType pType = "out".equalsIgnoreCase(request.getPunchType()) ? PunchType.out : PunchType.in;

        AttendanceLog log = attendanceService.registerPunch(
                member.getId(),
                member.getBiometricUid(),
                null, // No biometric device
                Instant.now(),
                pType,
                PunchSource.manual,
                request.getNote() != null && !request.getNote().trim().isEmpty() ? request.getNote() : "Manual check-in",
                ownerId
        );
        return ResponseEntity.ok(log);
    }

    @GetMapping("/monthly")
    public ResponseEntity<List<MonthlyAttendanceDto>> getMonthlyAttendance(
            @RequestParam(value = "member_id", required = false) Long memberIdAlias,
            @RequestParam(value = "memberId", required = false) Long memberId,
            @RequestParam("month") String monthStr) {
        
        Long resolvedMemberId = memberId != null ? memberId : memberIdAlias;
        if (resolvedMemberId == null) {
            throw new BadRequestException("member_id is required");
        }

        UUID ownerId = gymOwnerContext.getGymOwnerId();
        Member member = memberRepository.findByIdAndGymOwnerId(resolvedMemberId, ownerId)
                .orElseThrow(() -> new com.gympal.common.exceptions.NotFoundException("Member not found: " + resolvedMemberId));

        // Parse month (YYYY-MM)
        String[] parts = monthStr.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        List<AttendanceSummary> summaries = attendanceSummaryRepository
                .findByMemberIdAndGymOwnerIdAndAttendanceDateBetween(resolvedMemberId, ownerId, startDate, endDate);

        Map<LocalDate, AttendanceSummary> summaryMap = summaries.stream()
                .collect(Collectors.toMap(AttendanceSummary::getAttendanceDate, s -> s));

        List<MonthlyAttendanceDto> result = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            AttendanceSummary summary = summaryMap.get(current);
            if (summary != null) {
                result.add(new MonthlyAttendanceDto(
                        current.toString(),
                        true,
                        summary.getFirstIn() != null ? summary.getFirstIn().toString() : null,
                        summary.getLastOut() != null ? summary.getLastOut().toString() : null,
                        summary.getTotalPunches()
                ));
            } else {
                result.add(new MonthlyAttendanceDto(
                        current.toString(),
                        false,
                        null,
                        null,
                        0
                ));
            }
            current = current.plusDays(1);
        }

        return ResponseEntity.ok(result);
    }

    // Monthly Attendance DTO
    public static class MonthlyAttendanceDto {
        private String date;
        @JsonProperty("has_punch")
        private boolean hasPunch;
        @JsonProperty("first_in")
        private String firstIn;
        @JsonProperty("last_out")
        private String lastOut;
        @JsonProperty("punch_count")
        private int punchCount;

        public MonthlyAttendanceDto(String date, boolean hasPunch, String firstIn, String lastOut, int punchCount) {
            this.date = date;
            this.hasPunch = hasPunch;
            this.firstIn = firstIn;
            this.lastOut = lastOut;
            this.punchCount = punchCount;
        }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public boolean isHasPunch() { return hasPunch; }
        public void setHasPunch(boolean hasPunch) { this.hasPunch = hasPunch; }
        public String getFirstIn() { return firstIn; }
        public void setFirstIn(String firstIn) { this.firstIn = firstIn; }
        public String getLastOut() { return lastOut; }
        public void setLastOut(String lastOut) { this.lastOut = lastOut; }
        public int getPunchCount() { return punchCount; }
        public void setPunchCount(int punchCount) { this.punchCount = punchCount; }
    }

    // Manual Punch Request DTO
    public static class ManualPunchRequest {
        private Long memberId;
        private String punchType; // "in" or "out"
        private String note;

        public Long getMemberId() { return memberId; }
        public void setMemberId(Long memberId) { this.memberId = memberId; }
        public String getPunchType() { return punchType; }
        public void setPunchType(String punchType) { this.punchType = punchType; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    // Attendance Row structure
    public static class AttendanceRow {
        private Long memberId;
        private String memberName;
        private String biometricUid;
        private String mobileNumber;
        private LocalTime firstIn;
        private LocalTime lastOut;
        private int totalPunches;
        private String status; // present, absent

        public AttendanceRow(Long memberId, String memberName, String biometricUid, String mobileNumber, LocalTime firstIn, LocalTime lastOut, int totalPunches, String status) {
            this.memberId = memberId;
            this.memberName = memberName;
            this.biometricUid = biometricUid;
            this.mobileNumber = mobileNumber;
            this.firstIn = firstIn;
            this.lastOut = lastOut;
            this.totalPunches = totalPunches;
            this.status = status;
        }

        public Long getMemberId() { return memberId; }
        public String getMemberName() { return memberName; }
        public String getBiometricUid() { return biometricUid; }
        public String getMobileNumber() { return mobileNumber; }
        public LocalTime getFirstIn() { return firstIn; }
        public LocalTime getLastOut() { return lastOut; }
        public int getTotalPunches() { return totalPunches; }
        public String getStatus() { return status; }
    }

    // Punch Webhook DTO
    public static class PunchDto {
        private String biometricUid;
        private String punchTime;
        private String punchType;
        private String deviceSerial;

        public String getBiometricUid() { return biometricUid; }
        public void setBiometricUid(String biometricUid) { this.biometricUid = biometricUid; }
        public String getPunchTime() { return punchTime; }
        public void setPunchTime(String punchTime) { this.punchTime = punchTime; }
        public String getPunchType() { return punchType; }
        public void setPunchType(String punchType) { this.punchType = punchType; }
        public String getDeviceSerial() { return deviceSerial; }
        public void setDeviceSerial(String deviceSerial) { this.deviceSerial = deviceSerial; }
    }

    public static class PunchResponse {
        private int accepted;
        private int deduped;

        public PunchResponse(int accepted, int deduped) {
            this.accepted = accepted;
            this.deduped = deduped;
        }
        public int getAccepted() { return accepted; }
        public int getDeduped() { return deduped; }
    }
}
