package com.gympal.memberships;

import com.gympal.access.AccessService;
import com.gympal.common.enums.MembershipStatus;
import com.gympal.common.enums.PaymentMode;
import com.gympal.common.exceptions.NotFoundException;
import com.gympal.members.Member;
import com.gympal.members.MemberRepository;
import com.gympal.payments.PaymentService;
import com.gympal.payments.PaymentTransaction;
import com.gympal.payments.PaymentTransactionRepository;
import com.gympal.plans.Plan;
import com.gympal.plans.PlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class MembershipService {

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AccessService accessService;

    @Transactional
    public Membership assignMembership(Long memberId, AssignDto dto, UUID gymOwnerId) {
        Member member = memberRepository.findByIdAndGymOwnerId(memberId, gymOwnerId)
                .orElseThrow(() -> new NotFoundException("Member not found: " + memberId));

        Plan plan = planRepository.findByIdAndGymOwnerId(dto.getPlanId(), gymOwnerId)
                .orElseThrow(() -> new NotFoundException("Plan not found: " + dto.getPlanId()));

        LocalDate startDate = dto.getStartDate() != null ? dto.getStartDate() : LocalDate.now();
        LocalDate endDate = dto.getEndDate() != null ? dto.getEndDate() : startDate.plusDays(plan.getDurationDays());

        if (endDate.isBefore(startDate)) {
            throw new com.gympal.common.exceptions.BadRequestException("End date cannot be before start date");
        }

        if (!plan.isActive()) {
            throw new com.gympal.common.exceptions.BadRequestException("Cannot assign a deactivated plan: " + plan.getPlanName());
        }

        BigDecimal totalFee = dto.getTotalFee() != null ? dto.getTotalFee() : plan.getPrice();
        if (totalFee.signum() < 0) {
            throw new com.gympal.common.exceptions.BadRequestException("Total fee must be non-negative");
        }

        BigDecimal discountAmount = dto.getDiscountAmount() != null ? dto.getDiscountAmount() : BigDecimal.ZERO;
        if (discountAmount.signum() < 0) {
            throw new com.gympal.common.exceptions.BadRequestException("Discount must be non-negative");
        }
        if (discountAmount.compareTo(totalFee) > 0) {
            throw new com.gympal.common.exceptions.BadRequestException("Discount cannot exceed the total plan fee");
        }
        
        BigDecimal netPayable = totalFee.subtract(discountAmount);
        if (netPayable.compareTo(BigDecimal.ZERO) < 0) {
            netPayable = BigDecimal.ZERO;
        }

        BigDecimal firstPayment = dto.getFirstPaymentAmount() != null ? dto.getFirstPaymentAmount() : BigDecimal.ZERO;
        if (firstPayment.signum() < 0) {
            throw new com.gympal.common.exceptions.BadRequestException("First payment amount must be non-negative");
        }
        if (firstPayment.compareTo(netPayable) > 0) {
            throw new com.gympal.common.exceptions.BadRequestException("First payment amount cannot exceed the net payable amount: ₹" + netPayable);
        }

        // Determine initial membership status
        LocalDate today = LocalDate.now();
        MembershipStatus status = calculateInitialStatus(endDate, today);

        // Build Membership
        Membership membership = Membership.builder()
                .member(member)
                .plan(plan)
                .startDate(startDate)
                .endDate(endDate)
                .totalFee(totalFee)
                .discountAmount(discountAmount)
                .discountNote(dto.getDiscountNote())
                .paymentMode(dto.getPaymentMode())
                .paymentStatus("unpaid") // starts unpaid until transactions are recorded
                .status(status)
                .notes(dto.getNotes())
                .gymOwnerId(gymOwnerId)
                .build();

        membership = membershipRepository.save(membership);

        // Record first payment if any
        if (firstPayment.compareTo(BigDecimal.ZERO) > 0) {
            PaymentTransaction pt = PaymentTransaction.builder()
                    .membership(membership)
                    .member(member)
                    .amount(firstPayment)
                    .paymentMode(dto.getPaymentMode() != null ? dto.getPaymentMode().name() : "cash")
                    .paymentDate(startDate)
                    .note("First payment during plan assignment")
                    .gymOwnerId(gymOwnerId)
                    .build();
            paymentTransactionRepository.save(pt);
            
            // Recalculates amount_paid, payment_status, and triggers access sync
            paymentService.recalcMembershipPaid(membership.getId());
        } else {
            // Re-sync access anyway (e.g. member could become blocked if unpaid, or remains blocked)
            accessService.syncMemberAccessStatus(member.getId());
        }

        return membership;
    }

    private MembershipStatus calculateInitialStatus(LocalDate endDate, LocalDate today) {
        if (endDate.isBefore(today)) {
            return MembershipStatus.expired;
        } else if (!endDate.isAfter(today.plusDays(7))) {
            return MembershipStatus.expiring_soon;
        } else {
            return MembershipStatus.active;
        }
    }

    // Assign DTO
    public static class AssignDto {
        private Long planId;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal totalFee;
        private BigDecimal discountAmount;
        private String discountNote;
        private BigDecimal firstPaymentAmount;
        private PaymentMode paymentMode;
        private String notes;

        public Long getPlanId() { return planId; }
        public void setPlanId(Long planId) { this.planId = planId; }
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
        public BigDecimal getTotalFee() { return totalFee; }
        public void setTotalFee(BigDecimal totalFee) { this.totalFee = totalFee; }
        public BigDecimal getDiscountAmount() { return discountAmount; }
        public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
        public String getDiscountNote() { return discountNote; }
        public void setDiscountNote(String discountNote) { this.discountNote = discountNote; }
        public BigDecimal getFirstPaymentAmount() { return firstPaymentAmount; }
        public void setFirstPaymentAmount(BigDecimal firstPaymentAmount) { this.firstPaymentAmount = firstPaymentAmount; }
        public PaymentMode getPaymentMode() { return paymentMode; }
        public void setPaymentMode(PaymentMode paymentMode) { this.paymentMode = paymentMode; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }
}
