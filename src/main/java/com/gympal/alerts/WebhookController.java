package com.gympal.alerts;

import com.gympal.common.enums.NotifStatus;
import com.gympal.common.exceptions.UnauthorizedException;
import com.gympal.gym.GymOwner;
import com.gympal.gym.GymOwnerRepository;
import com.gympal.memberships.Membership;
import com.gympal.memberships.MembershipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    @Autowired
    private GymOwnerRepository gymOwnerRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Value("${jwt.secret}")
    private String webhookSecret;

    @PostMapping("/cron/expiry-reminders")
    public ResponseEntity<Map<String, Integer>> triggerExpiryReminders(
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestBody(required = false) String requestBody) {
        
        // HMAC Signature Verification
        byte[] bodyBytes = requestBody != null ? requestBody.getBytes() : new byte[0];
        if (!verifyHmacSignature(signature, bodyBytes)) {
            throw new UnauthorizedException("Invalid HMAC signature");
        }

        int queuedCount = 0;
        LocalDate today = LocalDate.now();

        List<GymOwner> owners = gymOwnerRepository.findAll();
        for (GymOwner gym : owners) {
            if (!gym.isAutoReminderEnabled()) {
                continue;
            }

            int daysBefore = gym.getReminderDaysBefore();
            LocalDate endThreshold = today.plusDays(daysBefore);

            // Find memberships ending between today and today + daysBefore
            List<Membership> expiringMemberships = membershipRepository.findExpiringSoonMemberships(
                    gym.getId(), today, endThreshold);

            for (Membership membership : expiringMemberships) {
                // Check if we have already queued a reminder for this membership
                List<NotificationLog> existingLogs = notificationLogRepository.findByMembershipIdAndGymOwnerId(
                        membership.getId(), gym.getId());

                boolean alreadyQueued = existingLogs.stream()
                        .anyMatch(log -> log.getCreatedAt().isAfter(today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));

                if (!alreadyQueued) {
                    String planName = membership.getPlan() != null ? membership.getPlan().getPlanName() : "Plan";
                    String message = String.format(
                            "Hi %s, your %s at %s expires on %s. Please renew.",
                            membership.getMember().getFullName(),
                            planName,
                            gym.getGymName(),
                            membership.getEndDate().toString()
                    );

                    NotificationLog log = NotificationLog.builder()
                            .membership(membership)
                            .memberName(membership.getMember().getFullName())
                            .mobileNumber(membership.getMember().getMobileNumber())
                            .message(message)
                            .status(NotifStatus.pending)
                            .gymOwnerId(gym.getId())
                            .build();

                    notificationLogRepository.save(log);
                    queuedCount++;
                }
            }
        }

        Map<String, Integer> response = new HashMap<>();
        response.put("queued", queuedCount);
        return ResponseEntity.ok(response);
    }

    private boolean verifyHmacSignature(String signature, byte[] body) {
        if (signature == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(webhookSecret.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(body);
            String calculatedSignature = Base64.getEncoder().encodeToString(hmacBytes);
            return calculatedSignature.equals(signature);
        } catch (Exception e) {
            return false;
        }
    }
}
