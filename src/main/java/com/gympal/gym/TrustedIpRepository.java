package com.gympal.gym;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrustedIpRepository extends JpaRepository<TrustedIp, Long> {
    List<TrustedIp> findByOwnerId(UUID ownerId);
    Optional<TrustedIp> findByOwnerIdAndIpAddress(UUID ownerId, String ipAddress);
}
