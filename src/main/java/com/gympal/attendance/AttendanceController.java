package com.gympal.attendance;

import com.gympal.common.GymOwnerContext;
import com.gympal.common.enums.AttendanceStatus;
import com.gympal.common.enums.PunchSource;
import com.gympal.common.enums.PunchType;
import com.gympal.common.exceptions.UnauthorizedException;
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
                }
            }
        }

        return ResponseEntity.ok(new PunchResponse(accepted, deduped));
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
