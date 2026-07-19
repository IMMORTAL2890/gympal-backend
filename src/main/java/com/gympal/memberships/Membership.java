package com.gympal.memberships;

import com.gympal.common.enums.MembershipStatus;
import com.gympal.common.enums.PaymentMode;
import com.gympal.members.Member;
import com.gympal.plans.Plan;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "memberships")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalFee;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "discount_note")
    private String discountNote;

    @Column(name = "net_payable", insertable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal netPayable;

    @Column(name = "amount_paid", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "due_amount", insertable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal dueAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode")
    private PaymentMode paymentMode;

    @Column(name = "payment_status", nullable = false)
    private String paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MembershipStatus status;

    private String notes;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "gym_owner_id", nullable = false)
    private UUID gymOwnerId;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (discountAmount == null) {
            discountAmount = BigDecimal.ZERO;
        }
        if (amountPaid == null) {
            amountPaid = BigDecimal.ZERO;
        }
    }
}
