package com.gympal.plans;

import com.gympal.common.GymOwnerContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    @Autowired
    private PlanService planService;

    @Autowired
    private GymOwnerContext gymOwnerContext;

    @GetMapping
    public ResponseEntity<List<PlanResponse>> getPlans() {
        List<Plan> plans = planService.getAllPlans(gymOwnerContext.getGymOwnerId());
        List<PlanResponse> response = plans.stream().map(p -> {
            long count = planService.getActiveMembershipCount(p.getId());
            return new PlanResponse(p, count);
        }).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<Plan> createPlan(@RequestBody Plan plan) {
        return ResponseEntity.ok(planService.createPlan(plan, gymOwnerContext.getGymOwnerId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Plan> updatePlan(@PathVariable Long id, @RequestBody Plan planDetails) {
        return ResponseEntity.ok(planService.updatePlan(id, planDetails, gymOwnerContext.getGymOwnerId()));
    }

    // Response structure with active count
    public static class PlanResponse {
        private Long id;
        private String planName;
        private int durationDays;
        private BigDecimal price;
        private String description;
        private boolean isActive;
        private long activeMembershipsCount;

        public PlanResponse(Plan p, long activeCount) {
            this.id = p.getId();
            this.planName = p.getPlanName();
            this.durationDays = p.getDurationDays();
            this.price = p.getPrice();
            this.description = p.getDescription();
            this.isActive = p.isActive();
            this.activeMembershipsCount = activeCount;
        }

        public Long getId() { return id; }
        public String getPlanName() { return planName; }
        public int getDurationDays() { return durationDays; }
        public BigDecimal getPrice() { return price; }
        public String getDescription() { return description; }
        public boolean getIsActive() { return isActive; }
        public long getActiveMembershipsCount() { return activeMembershipsCount; }
    }
}
