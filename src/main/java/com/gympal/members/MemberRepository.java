package com.gympal.members;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByIdAndGymOwnerId(Long id, UUID gymOwnerId);
    Optional<Member> findByGymOwnerIdAndBiometricUid(UUID gymOwnerId, String biometricUid);
    List<Member> findByGymOwnerId(UUID gymOwnerId);

    @Query("SELECT m FROM Member m WHERE m.gymOwnerId = :gymOwnerId AND " +
           "(COALESCE(:query, '') = '' OR LOWER(m.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR m.mobileNumber LIKE CONCAT('%', :query, '%'))")
    List<Member> searchMembers(@Param("gymOwnerId") UUID gymOwnerId, @Param("query") String query);

    boolean existsByGymOwnerIdAndMobileNumber(UUID gymOwnerId, String mobileNumber);
}
