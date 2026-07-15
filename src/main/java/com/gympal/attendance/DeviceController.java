package com.gympal.attendance;

import com.gympal.common.GymOwnerContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private GymOwnerContext gymOwnerContext;

    @GetMapping
    public ResponseEntity<List<BiometricDevice>> getDevices() {
        return ResponseEntity.ok(deviceService.getAllDevices(gymOwnerContext.getGymOwnerId()));
    }

    @PostMapping
    public ResponseEntity<BiometricDevice> createDevice(@RequestBody BiometricDevice device) {
        return ResponseEntity.ok(deviceService.createDevice(device, gymOwnerContext.getGymOwnerId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<BiometricDevice> updateDevice(@PathVariable Long id, @RequestBody BiometricDevice details) {
        return ResponseEntity.ok(deviceService.updateDevice(id, details, gymOwnerContext.getGymOwnerId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDevice(@PathVariable Long id) {
        deviceService.deleteDevice(id, gymOwnerContext.getGymOwnerId());
        return ResponseEntity.noContent().build();
    }
}
