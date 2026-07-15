package com.gympal.payments;

import com.gympal.common.GymOwnerContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private GymOwnerContext gymOwnerContext;

    @PostMapping("/memberships/{membershipId}/payments")
    public ResponseEntity<PaymentTransactionResponse> addPayment(
            @PathVariable Long membershipId,
            @RequestBody PaymentService.AddPaymentDto dto) {
        UUID ownerId = gymOwnerContext.getGymOwnerId();
        PaymentTransaction pt = paymentService.addPayment(membershipId, dto, ownerId);
        return ResponseEntity.ok(new PaymentTransactionResponse(pt));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<PaymentTransactionResponse>> getTransactions(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam(value = "memberId", required = false) Long memberId) {
        
        UUID ownerId = gymOwnerContext.getGymOwnerId();
        List<PaymentTransaction> list = paymentTransactionRepository.findByGymOwnerId(ownerId);

        // Apply filters
        List<PaymentTransactionResponse> response = list.stream()
                .filter(pt -> from == null || !pt.getPaymentDate().isBefore(from))
                .filter(pt -> to == null || !pt.getPaymentDate().isAfter(to))
                .filter(pt -> mode == null || mode.isBlank() || pt.getPaymentMode().equalsIgnoreCase(mode))
                .filter(pt -> memberId == null || pt.getMember().getId().equals(memberId))
                .sorted(Comparator.comparing(PaymentTransaction::getPaymentDate).reversed()
                        .thenComparing(Comparator.comparing(PaymentTransaction::getCreatedAt).reversed()))
                .map(PaymentTransactionResponse::new)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // DTO for clean JSON response mapping
    public static class PaymentTransactionResponse {
        private Long id;
        private Long membershipId;
        private Long memberId;
        private String memberName;
        private String planName;
        private java.math.BigDecimal amount;
        private String paymentMode;
        private LocalDate paymentDate;
        private String note;
        private java.time.Instant createdAt;

        public PaymentTransactionResponse(PaymentTransaction pt) {
            this.id = pt.getId();
            this.membershipId = pt.getMembership().getId();
            this.memberId = pt.getMember().getId();
            this.memberName = pt.getMember().getFullName();
            this.planName = pt.getMembership().getPlan() != null ? pt.getMembership().getPlan().getPlanName() : "Custom";
            this.amount = pt.getAmount();
            this.paymentMode = pt.getPaymentMode();
            this.paymentDate = pt.getPaymentDate();
            this.note = pt.getNote();
            this.createdAt = pt.getCreatedAt();
        }

        public Long getId() { return id; }
        public Long getMembershipId() { return membershipId; }
        public Long getMemberId() { return memberId; }
        public String getMemberName() { return memberName; }
        public String getPlanName() { return planName; }
        public java.math.BigDecimal getAmount() { return amount; }
        public String getPaymentMode() { return paymentMode; }
        public LocalDate getPaymentDate() { return paymentDate; }
        public String getNote() { return note; }
        public java.time.Instant getCreatedAt() { return createdAt; }
    }
}
