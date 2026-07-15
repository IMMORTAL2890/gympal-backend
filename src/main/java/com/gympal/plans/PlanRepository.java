package com.gympal.plans;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {
    List<Plan> findByGymOwnerId(UUID gymOwnerId);
    Optional<Plan> findByIdAndGymOwnerId(Long id, UUID gymOwnerId);
}
