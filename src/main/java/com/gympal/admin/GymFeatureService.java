package com.gympal.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class GymFeatureService {

    @Autowired
    private GymFeatureRepository gymFeatureRepository;

    @Autowired
    private FeatureAuditLogRepository featureAuditLogRepository;

    public boolean isFeatureEnabled(UUID gymId, GymFeatureKey featureKey) {
        return gymFeatureRepository.findById(new GymFeatureId(gymId, featureKey.name()))
                .map(GymFeature::isEnabled)
                .orElse(true);
    }

    public List<GymFeature> getGymFeatures(UUID gymId) {
        List<GymFeature> features = gymFeatureRepository.findByGymId(gymId);
        Set<String> existingKeys = features.stream().map(GymFeature::getFeatureKey).collect(java.util.stream.Collectors.toSet());
        List<GymFeature> allFeatures = new ArrayList<>(features);
        for (GymFeatureKey key : GymFeatureKey.values()) {
            if (!existingKeys.contains(key.name())) {
                allFeatures.add(GymFeature.builder()
                        .gymId(gymId)
                        .featureKey(key.name())
                        .enabled(true)
                        .updatedAt(Instant.now())
                        .build());
            }
        }
        return allFeatures;
    }

    @Transactional
    public GymFeature toggleFeature(UUID gymId, String featureKey, boolean enabled, UUID adminId) {
        GymFeatureId id = new GymFeatureId(gymId, featureKey);
        GymFeature feature = gymFeatureRepository.findById(id)
                .orElseGet(() -> GymFeature.builder()
                        .gymId(gymId)
                        .featureKey(featureKey)
                        .enabled(true)
                        .build());

        boolean oldVal = feature.isEnabled();
        feature.setEnabled(enabled);
        feature.setUpdatedBy(adminId != null ? adminId.toString() : "admin");
        feature.setUpdatedAt(Instant.now());
        gymFeatureRepository.save(feature);

        // Audit Log
        FeatureAuditLog audit = FeatureAuditLog.builder()
                .adminId(adminId)
                .gymId(gymId)
                .featureKey(featureKey)
                .oldValue(oldVal)
                .newValue(enabled)
                .build();
        featureAuditLogRepository.save(audit);

        return feature;
    }

    @Transactional
    public void bulkApplyPreset(UUID gymId, String planTier, UUID adminId) {
        Set<GymFeatureKey> enabledFeatures = new HashSet<>();
        if ("PREMIUM".equalsIgnoreCase(planTier)) {
            enabledFeatures.addAll(Arrays.asList(GymFeatureKey.values()));
        } else if ("BASIC".equalsIgnoreCase(planTier)) {
            enabledFeatures.addAll(Arrays.asList(
                GymFeatureKey.MEMBER_MANAGEMENT,
                GymFeatureKey.ATTENDANCE_CHECKIN,
                GymFeatureKey.SMS_WHATSAPP_NOTIFICATIONS,
                GymFeatureKey.ONLINE_PAYMENTS
            ));
        } else {
            enabledFeatures.addAll(Arrays.asList(
                GymFeatureKey.MEMBER_MANAGEMENT,
                GymFeatureKey.ATTENDANCE_CHECKIN
            ));
        }

        for (GymFeatureKey key : GymFeatureKey.values()) {
            boolean enable = enabledFeatures.contains(key);
            toggleFeature(gymId, key.name(), enable, adminId);
        }
    }

    public List<FeatureAuditLog> getAuditLogs(UUID gymId) {
        return featureAuditLogRepository.findByGymIdOrderByTimestampDesc(gymId);
    }
}
