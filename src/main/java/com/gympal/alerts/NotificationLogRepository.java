package com.gympal.alerts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    Optional<NotificationLog> findByIdAndGymOwnerId(Long id, UUID gymOwnerId);
    List<NotificationLog> findByMembershipIdAndGymOwnerId(Long membershipId, UUID gymOwnerId);
    List<NotificationLog> findByGymOwnerId(UUID gymOwnerId);
}
