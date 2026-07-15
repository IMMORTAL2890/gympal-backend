package com.gympal.payments;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByIdAndGymOwnerId(Long id, UUID gymOwnerId);
    List<PaymentTransaction> findByMembershipIdAndGymOwnerId(Long membershipId, UUID gymOwnerId);
    List<PaymentTransaction> findByMemberIdAndGymOwnerId(Long memberId, UUID gymOwnerId);
    List<PaymentTransaction> findByGymOwnerId(UUID gymOwnerId);

    @Query("SELECT COALESCE(SUM(pt.amount), 0) FROM PaymentTransaction pt WHERE pt.membership.id = :membershipId")
    BigDecimal sumAmountByMembershipId(@Param("membershipId") Long membershipId);

    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.gymOwnerId = :gymOwnerId AND pt.paymentDate BETWEEN :startDate AND :endDate")
    List<PaymentTransaction> findByGymOwnerIdAndDateRange(@Param("gymOwnerId") UUID gymOwnerId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
