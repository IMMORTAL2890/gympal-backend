package com.gympal.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BiometricDeviceRepository extends JpaRepository<BiometricDevice, Long> {
    Optional<BiometricDevice> findByIdAndGymOwnerId(Long id, UUID gymOwnerId);
    List<BiometricDevice> findByGymOwnerId(UUID gymOwnerId);
    boolean existsByGymOwnerIdAndDeviceSerial(UUID gymOwnerId, String deviceSerial);
    boolean existsByGymOwnerIdAndDeviceIp(UUID gymOwnerId, String deviceIp);
}
