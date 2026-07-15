package com.gympal.alerts;

import com.gympal.common.GymOwnerContext;
import com.gympal.common.enums.NotifStatus;
import com.gympal.common.exceptions.NotFoundException;
import com.gympal.memberships.Membership;
import com.gympal.memberships.MembershipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class AlertController {

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Autowired
    private GymOwnerContext gymOwnerContext;

    @GetMapping("/alerts")
    public ResponseEntity<AlertsResponse> getAlerts() {
        UUID ownerId = gymOwnerContext.getGymOwnerId();
        List<Membership> memberships = membershipRepository.findByGymOwnerId(ownerId);
        LocalDate today = LocalDate.now();

        // 1. Expiring soon alerts: endDate between today and today + 7 days
        List<ExpiringAlertDto> expiring = memberships.stream()
                .filter(m -> !m.getEndDate().isBefore(today) && !m.getEndDate().isAfter(today.plusDays(7)))
                .map(m -> new ExpiringAlertDto(
                        m.getId(),
                        m.getMember().getId(),
                        m.getMember().getFullName(),
                        m.getMember().getMobileNumber(),
                        m.getPlan() != null ? m.getPlan().getPlanName() : "Custom Plan",
                        m.getEndDate(),
                        ChronoUnit.DAYS.between(today, m.getEndDate())
                ))
                .sorted(Comparator.comparing(ExpiringAlertDto::getEndDate))
                .collect(Collectors.toList());

        // 2. Outstanding dues alerts: dueAmount > 0
        List<DuesAlertDto> dues = memberships.stream()
                .filter(m -> {
                    BigDecimal net = m.getTotalFee().subtract(m.getDiscountAmount());
                    BigDecimal due = net.subtract(m.getAmountPaid());
                    return due.compareTo(BigDecimal.ZERO) > 0;
                })
                .map(m -> {
                    BigDecimal net = m.getTotalFee().subtract(m.getDiscountAmount());
                    BigDecimal due = net.subtract(m.getAmountPaid());
                    return new DuesAlertDto(
                            m.getId(),
                            m.getMember().getId(),
                            m.getMember().getFullName(),
                            m.getMember().getMobileNumber(),
                            m.getPlan() != null ? m.getPlan().getPlanName() : "Custom Plan",
                            due,
                            m.getEndDate()
                    );
                })
                .sorted(Comparator.comparing(DuesAlertDto::getDueAmount).reversed())
                .collect(Collectors.toList());

        return ResponseEntity.ok(new AlertsResponse(expiring, dues));
    }

    @PostMapping("/notifications/log")
    public ResponseEntity<NotificationLog> logNotification(@RequestBody NotificationLogDto dto) {
        UUID ownerId = gymOwnerContext.getGymOwnerId();
        Membership membership = membershipRepository.findByIdAndGymOwnerId(dto.getMembershipId(), ownerId)
                .orElseThrow(() -> new NotFoundException("Membership not found: " + dto.getMembershipId()));

        NotificationLog log = NotificationLog.builder()
                .membership(membership)
                .memberName(membership.getMember().getFullName())
                .mobileNumber(membership.getMember().getMobileNumber())
                .message(dto.getMessage())
                .status(dto.getStatus() != null ? dto.getStatus() : NotifStatus.pending)
                .gymOwnerId(ownerId)
                .build();

        return ResponseEntity.ok(notificationLogRepository.save(log));
    }

    // Response models
    public static class AlertsResponse {
        private List<ExpiringAlertDto> expiring;
        private List<DuesAlertDto> dues;

        public AlertsResponse(List<ExpiringAlertDto> expiring, List<DuesAlertDto> dues) {
            this.expiring = expiring;
            this.dues = dues;
        }

        public List<ExpiringAlertDto> getExpiring() { return expiring; }
        public List<DuesAlertDto> getDues() { return dues; }
    }

    public static class ExpiringAlertDto {
        private Long membershipId;
        private Long memberId;
        private String memberName;
        private String mobileNumber;
        private String planName;
        private LocalDate endDate;
        private long daysRemaining;

        public ExpiringAlertDto(Long membershipId, Long memberId, String memberName, String mobileNumber, String planName, LocalDate endDate, long daysRemaining) {
            this.membershipId = membershipId;
            this.memberId = memberId;
            this.memberName = memberName;
            this.mobileNumber = mobileNumber;
            this.planName = planName;
            this.endDate = endDate;
            this.daysRemaining = daysRemaining;
        }

        public Long getMembershipId() { return membershipId; }
        public Long getMemberId() { return memberId; }
        public String getMemberName() { return memberName; }
        public String getMobileNumber() { return mobileNumber; }
        public String getPlanName() { return planName; }
        public LocalDate getEndDate() { return endDate; }
        public long getDaysRemaining() { return daysRemaining; }
    }

    public static class DuesAlertDto {
        private Long membershipId;
        private Long memberId;
        private String memberName;
        private String mobileNumber;
        private String planName;
        private BigDecimal dueAmount;
        private LocalDate endDate;

        public DuesAlertDto(Long membershipId, Long memberId, String memberName, String mobileNumber, String planName, BigDecimal dueAmount, LocalDate endDate) {
            this.membershipId = membershipId;
            this.memberId = memberId;
            this.memberName = memberName;
            this.mobileNumber = mobileNumber;
            this.planName = planName;
            this.dueAmount = dueAmount;
            this.endDate = endDate;
        }

        public Long getMembershipId() { return membershipId; }
        public Long getMemberId() { return memberId; }
        public String getMemberName() { return memberName; }
        public String getMobileNumber() { return mobileNumber; }
        public String getPlanName() { return planName; }
        public BigDecimal getDueAmount() { return dueAmount; }
        public LocalDate getEndDate() { return endDate; }
    }

    public static class NotificationLogDto {
        private Long membershipId;
        private String message;
        private NotifStatus status;

        public Long getMembershipId() { return membershipId; }
        public void setMembershipId(Long membershipId) { this.membershipId = membershipId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public NotifStatus getStatus() { return status; }
        public void setStatus(NotifStatus status) { this.status = status; }
    }
}
