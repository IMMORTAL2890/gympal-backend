package com.gympal.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface FeatureAuditLogRepository extends JpaRepository<FeatureAuditLog, UUID> {
    List<FeatureAuditLog> findByGymIdOrderByTimestampDesc(UUID gymId);
}
