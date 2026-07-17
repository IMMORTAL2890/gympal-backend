package com.gympal.memberships;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@CrossOrigin(origins = "*")
public interface MembershipRepository extends JpaRepository<Membership, Long> {
    Optional<Membership> findByIdAndGymOwnerId(Long id, UUID gymOwnerId);
    List<Membership> findByMemberIdAndGymOwnerId(Long memberId, UUID gymOwnerId);
    List<Membership> findByGymOwnerId(UUID gymOwnerId);

    @Query("SELECT m FROM Membership m WHERE m.gymOwnerId = :gymOwnerId AND m.endDate >= :today")
    List<Membership> findActiveMemberships(@Param("gymOwnerId") UUID gymOwnerId, @Param("today") LocalDate today);

    @Query("SELECT m FROM Membership m WHERE m.gymOwnerId = :gymOwnerId AND m.endDate < :today")
    List<Membership> findExpiredMemberships(@Param("gymOwnerId") UUID gymOwnerId, @Param("today") LocalDate today);

    @Query("SELECT m FROM Membership m WHERE m.gymOwnerId = :gymOwnerId AND m.endDate >= :today AND m.endDate <= :endThreshold")
    List<Membership> findExpiringSoonMemberships(@Param("gymOwnerId") UUID gymOwnerId, @Param("today") LocalDate today, @Param("endThreshold") LocalDate endThreshold);

    @Query("SELECT COUNT(m) FROM Membership m WHERE m.plan.id = :planId AND m.endDate >= :today")
    long countActiveMembershipsForPlan(@Param("planId") Long planId, @Param("today") LocalDate today);
}
