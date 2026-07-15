package com.gympal.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface GymFeatureRepository extends JpaRepository<GymFeature, GymFeatureId> {
    List<GymFeature> findByGymId(UUID gymId);
}
