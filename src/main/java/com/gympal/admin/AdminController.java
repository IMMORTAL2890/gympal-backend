package com.gympal.admin;

import com.gympal.common.GymOwnerContext;
import com.gympal.gym.GymOwner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private GymFeatureService gymFeatureService;

    @Autowired
    private GymOwnerContext gymOwnerContext;

    @GetMapping("/dashboard")
    public ResponseEntity<AdminService.AdminDashboardStatsDto> getDashboard() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    @GetMapping("/gyms")
    public ResponseEntity<List<AdminService.GymSummaryDto>> getGyms() {
        return ResponseEntity.ok(adminService.getPlatformGymSummary());
    }

    @GetMapping("/gyms/{gymId}")
    public ResponseEntity<AdminService.GymDetailDto> getGymDetail(@PathVariable UUID gymId) {
        return ResponseEntity.ok(adminService.getPlatformGymDetail(gymId));
    }

    @PatchMapping("/gyms/{gymId}/status")
    public ResponseEntity<GymOwner> updateGymStatus(
            @PathVariable UUID gymId,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        String subscriptionPlan = body.get("subscriptionPlan");
        return ResponseEntity.ok(adminService.updateGymStatus(gymId, status, subscriptionPlan));
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminService.AdminUserDto>> getUsers(
            @RequestParam(required = false) UUID gymId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String plan) {
        return ResponseEntity.ok(adminService.getPlatformUsers(gymId, status, plan));
    }

    @GetMapping("/revenue")
    public ResponseEntity<AdminService.PlatformRevenueDto> getRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(adminService.getPlatformRevenue(start, end));
    }

    @GetMapping("/revenue/export")
    public ResponseEntity<byte[]> exportRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        AdminService.PlatformRevenueDto revenue = adminService.getPlatformRevenue(start, end);
        
        StringBuilder csv = new StringBuilder("Gym ID,Gym Name,Owner Name,Revenue (INR)\n");
        for (AdminService.GymRevenueDto r : revenue.getGymBreakdown()) {
            csv.append(String.format("%s,%s,%s,%s\n",
                    r.getGymId(),
                    r.getGymName().replace(",", " "),
                    r.getOwnerName().replace(",", " "),
                    r.getRevenue().toString()
            ));
        }
        csv.append(String.format("TOTAL,,,%s\n", revenue.getTotalRevenue().toString()));

        byte[] data = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=revenue-report.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(data);
    }

    @GetMapping("/gyms/{gymId}/features")
    public ResponseEntity<List<GymFeature>> getFeatures(@PathVariable UUID gymId) {
        return ResponseEntity.ok(gymFeatureService.getGymFeatures(gymId));
    }

    @PostMapping("/gyms/{gymId}/features")
    public ResponseEntity<GymFeature> toggleFeature(
            @PathVariable UUID gymId,
            @RequestBody Map<String, Object> body) {
        String key = (String) body.get("featureKey");
        boolean enabled = (Boolean) body.get("enabled");
        UUID adminId = gymOwnerContext.getAuthUserId();
        return ResponseEntity.ok(gymFeatureService.toggleFeature(gymId, key, enabled, adminId));
    }

    @PostMapping("/gyms/{gymId}/features/bulk-apply")
    public ResponseEntity<Map<String, String>> bulkApplyPreset(
            @PathVariable UUID gymId,
            @RequestBody Map<String, String> body) {
        String preset = body.get("planTier");
        UUID adminId = gymOwnerContext.getAuthUserId();
        gymFeatureService.bulkApplyPreset(gymId, preset, adminId);
        return ResponseEntity.ok(Map.of("message", "Preset applied successfully"));
    }

    @GetMapping("/gyms/{gymId}/features/audit")
    public ResponseEntity<List<FeatureAuditLog>> getAuditLogs(@PathVariable UUID gymId) {
        return ResponseEntity.ok(gymFeatureService.getAuditLogs(gymId));
    }
}
