package com.gympal.attendance;

import com.gympal.common.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DeviceService {

    @Autowired
    private BiometricDeviceRepository biometricDeviceRepository;

    public List<BiometricDevice> getAllDevices(UUID gymOwnerId) {
        return biometricDeviceRepository.findByGymOwnerId(gymOwnerId);
    }

    public BiometricDevice getDeviceById(Long id, UUID gymOwnerId) {
        return biometricDeviceRepository.findByIdAndGymOwnerId(id, gymOwnerId)
                .orElseThrow(() -> new NotFoundException("Device not found: " + id));
    }

    @Transactional
    public BiometricDevice createDevice(BiometricDevice device, UUID gymOwnerId) {
        device.setGymOwnerId(gymOwnerId);
        
        if (device.getDeviceName() == null || device.getDeviceName().isBlank()) {
            throw new IllegalArgumentException("Device nickname is required");
        }
        if (device.getDeviceIp() == null || !device.getDeviceIp().matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")) {
            throw new IllegalArgumentException("Device IP must be a valid IPv4 address");
        }
        if (device.getDevicePort() <= 0 || device.getDevicePort() > 65535) {
            throw new IllegalArgumentException("Device connection port must be between 1 and 65535");
        }
        if (device.getDeviceSerial() == null || device.getDeviceSerial().isBlank()) {
            throw new IllegalArgumentException("Device serial number is required");
        }
        if (biometricDeviceRepository.existsByGymOwnerIdAndDeviceSerial(gymOwnerId, device.getDeviceSerial())) {
            throw new IllegalArgumentException("A device with this serial number is already registered");
        }
        if (biometricDeviceRepository.existsByGymOwnerIdAndDeviceIp(gymOwnerId, device.getDeviceIp())) {
            throw new IllegalArgumentException("A device with this IP address is already registered");
        }
        
        return biometricDeviceRepository.save(device);
    }

    @Transactional
    public BiometricDevice updateDevice(Long id, BiometricDevice details, UUID gymOwnerId) {
        BiometricDevice device = getDeviceById(id, gymOwnerId);

        if (details.getDeviceName() != null) {
            if (details.getDeviceName().isBlank()) {
                throw new IllegalArgumentException("Device name cannot be blank");
            }
            device.setDeviceName(details.getDeviceName());
        }
        if (details.getDeviceIp() != null) {
            if (!details.getDeviceIp().matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")) {
                throw new IllegalArgumentException("Device IP must be a valid IPv4 address");
            }
            if (!details.getDeviceIp().equals(device.getDeviceIp())) {
                if (biometricDeviceRepository.existsByGymOwnerIdAndDeviceIp(gymOwnerId, details.getDeviceIp())) {
                    throw new IllegalArgumentException("A device with this IP address is already registered");
                }
            }
            device.setDeviceIp(details.getDeviceIp());
        }
        if (details.getDevicePort() != 0) {
            if (details.getDevicePort() <= 0 || details.getDevicePort() > 65535) {
                throw new IllegalArgumentException("Device connection port must be between 1 and 65535");
            }
            device.setDevicePort(details.getDevicePort());
        }
        if (details.getDeviceSerial() != null) {
            if (details.getDeviceSerial().isBlank()) {
                throw new IllegalArgumentException("Device serial number cannot be empty");
            }
            if (!details.getDeviceSerial().equals(device.getDeviceSerial())) {
                if (biometricDeviceRepository.existsByGymOwnerIdAndDeviceSerial(gymOwnerId, details.getDeviceSerial())) {
                    throw new IllegalArgumentException("A device with this serial number is already registered");
                }
            }
            device.setDeviceSerial(details.getDeviceSerial());
        }
        if (details.getDevicePassword() != null) device.setDevicePassword(details.getDevicePassword());
        if (details.getDeviceModel() != null) device.setDeviceModel(details.getDeviceModel());
        if (details.getFirmwareVersion() != null) device.setFirmwareVersion(details.getFirmwareVersion());
        if (details.getLocation() != null) device.setLocation(details.getLocation());
        
        device.setActive(details.isActive());
        device.setDoorControlEnabled(details.isDoorControlEnabled());
        
        if (details.getLastSyncTime() != null) device.setLastSyncTime(details.getLastSyncTime());
        if (details.getLastSyncStatus() != null) device.setLastSyncStatus(details.getLastSyncStatus());
        if (details.getLastSyncCount() >= 0) device.setLastSyncCount(details.getLastSyncCount());

        return biometricDeviceRepository.save(device);
    }

    @Transactional
    public void deleteDevice(Long id, UUID gymOwnerId) {
        BiometricDevice device = getDeviceById(id, gymOwnerId);
        biometricDeviceRepository.delete(device);
    }
}
