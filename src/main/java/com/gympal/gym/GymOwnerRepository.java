package com.gympal.gym;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GymOwnerRepository extends JpaRepository<GymOwner, UUID> {
    Optional<GymOwner> findByAuthUserId(UUID authUserId);
}
