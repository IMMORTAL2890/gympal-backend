package com.gympal.attendance;

import com.gympal.common.enums.DeviceSyncStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "biometric_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BiometricDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_name", nullable = false)
    private String deviceName;

    @Column(name = "device_ip", nullable = false)
    private String deviceIp;

    @Column(name = "device_port")
    @Builder.Default
    private int devicePort = 4370;

    @Column(name = "device_password")
    private String devicePassword;

    @Column(name = "device_model")
    private String deviceModel;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "device_serial")
    private String deviceSerial;

    private String location;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "door_control_enabled")
    @Builder.Default
    private boolean doorControlEnabled = false;

    @Column(name = "last_sync_time")
    private Instant lastSyncTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_sync_status", columnDefinition = "device_sync_status")
    @Builder.Default
    private DeviceSyncStatus lastSyncStatus = DeviceSyncStatus.never;

    @Column(name = "last_sync_count")
    @Builder.Default
    private int lastSyncCount = 0;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "gym_owner_id", nullable = false)
    private UUID gymOwnerId;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
