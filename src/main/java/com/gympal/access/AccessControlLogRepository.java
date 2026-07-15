package com.gympal.access;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccessControlLogRepository extends JpaRepository<AccessControlLog, Long> {
    Optional<AccessControlLog> findByIdAndGymOwnerId(Long id, UUID gymOwnerId);
    List<AccessControlLog> findByMemberIdAndGymOwnerId(Long memberId, UUID gymOwnerId);
    List<AccessControlLog> findByGymOwnerId(UUID gymOwnerId);
}
