package com.gympal.payments;

import com.gympal.access.AccessService;
import com.gympal.memberships.Membership;
import com.gympal.memberships.MembershipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class PaymentService {

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private AccessService accessService;

    @Transactional
    public void recalcMembershipPaid(Long membershipId) {
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Membership not found: " + membershipId));

        BigDecimal amountPaid = paymentTransactionRepository.sumAmountByMembershipId(membershipId);
        if (amountPaid == null) {
            amountPaid = BigDecimal.ZERO;
        }
        
        membership.setAmountPaid(amountPaid);

        BigDecimal netPayable = membership.getTotalFee().subtract(membership.getDiscountAmount());
        if (netPayable.compareTo(BigDecimal.ZERO) < 0) {
            netPayable = BigDecimal.ZERO;
        }

        // recalculate payment status text
        if (amountPaid.compareTo(BigDecimal.ZERO) == 0) {
            membership.setPaymentStatus("unpaid");
        } else if (amountPaid.compareTo(netPayable) >= 0) {
            membership.setPaymentStatus("paid");
        } else {
            membership.setPaymentStatus("partial");
        }

        membershipRepository.save(membership);

        // Sync access status of the member as their active membership status could have changed
        accessService.syncMemberAccessStatus(membership.getMember().getId());
    }

    @Transactional
    public PaymentTransaction addPayment(Long membershipId, AddPaymentDto dto, java.util.UUID gymOwnerId) {
        Membership membership = membershipRepository.findByIdAndGymOwnerId(membershipId, gymOwnerId)
                .orElseThrow(() -> new com.gympal.common.exceptions.NotFoundException("Membership not found: " + membershipId));

        BigDecimal amount = dto.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new com.gympal.common.exceptions.BadRequestException("Payment amount must be greater than 0");
        }

        BigDecimal netPayable = membership.getTotalFee().subtract(membership.getDiscountAmount());
        if (netPayable.compareTo(BigDecimal.ZERO) < 0) {
            netPayable = BigDecimal.ZERO;
        }

        BigDecimal currentDue = netPayable.subtract(membership.getAmountPaid());
        if (currentDue.compareTo(BigDecimal.ZERO) < 0) {
            currentDue = BigDecimal.ZERO;
        }

        if (amount.compareTo(currentDue) > 0) {
            throw new com.gympal.common.exceptions.BadRequestException("Payment amount cannot exceed the outstanding due amount: ₹" + currentDue);
        }

        java.time.LocalDate paymentDate = dto.getPaymentDate() != null ? dto.getPaymentDate() : java.time.LocalDate.now();
        if (paymentDate.isAfter(java.time.LocalDate.now())) {
            throw new com.gympal.common.exceptions.BadRequestException("Payment date cannot be in the future");
        }

        PaymentTransaction pt = PaymentTransaction.builder()
                .membership(membership)
                .member(membership.getMember())
                .amount(amount)
                .paymentMode(dto.getMode() != null ? dto.getMode() : "cash")
                .paymentDate(paymentDate)
                .note(dto.getNote())
                .gymOwnerId(gymOwnerId)
                .build();

        paymentTransactionRepository.save(pt);

        // Recalculate membership paid amount and update status
        recalcMembershipPaid(membershipId);

        return pt;
    }

    public static class AddPaymentDto {
        private BigDecimal amount;
        private String mode;
        private java.time.LocalDate paymentDate;
        private String note;

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public java.time.LocalDate getPaymentDate() { return paymentDate; }
        public void setPaymentDate(java.time.LocalDate paymentDate) { this.paymentDate = paymentDate; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }
}

