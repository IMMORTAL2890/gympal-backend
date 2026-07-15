package com.gympal.plans;

import com.gympal.common.exceptions.NotFoundException;
import com.gympal.memberships.MembershipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PlanService {

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    public List<Plan> getAllPlans(UUID gymOwnerId) {
        return planRepository.findByGymOwnerId(gymOwnerId);
    }

    public Plan getPlanById(Long id, UUID gymOwnerId) {
        return planRepository.findByIdAndGymOwnerId(id, gymOwnerId)
                .orElseThrow(() -> new NotFoundException("Plan not found: " + id));
    }

    @Transactional
    public Plan createPlan(Plan plan, UUID gymOwnerId) {
        plan.setGymOwnerId(gymOwnerId);
        if (plan.getPlanName() == null || plan.getPlanName().isBlank()) {
            throw new IllegalArgumentException("Plan name is required");
        }
        if (plan.getDurationDays() <= 0) {
            throw new IllegalArgumentException("Duration must be greater than 0 days");
        }
        if (plan.getPrice() == null || plan.getPrice().signum() < 0) {
            throw new IllegalArgumentException("Price must be non-negative");
        }
        return planRepository.save(plan);
    }

    @Transactional
    public Plan updatePlan(Long id, Plan planDetails, UUID gymOwnerId) {
        Plan plan = getPlanById(id, gymOwnerId);

        if (planDetails.getPlanName() != null) {
            if (planDetails.getPlanName().isBlank()) {
                throw new IllegalArgumentException("Plan name cannot be blank");
            }
            plan.setPlanName(planDetails.getPlanName());
        }
        if (planDetails.getDurationDays() > 0) {
            plan.setDurationDays(planDetails.getDurationDays());
        }
        if (planDetails.getPrice() != null) {
            if (planDetails.getPrice().signum() < 0) {
                throw new IllegalArgumentException("Price must be non-negative");
            }
            plan.setPrice(planDetails.getPrice());
        }
        if (planDetails.getDescription() != null) {
            plan.setDescription(planDetails.getDescription());
        }
        
        plan.setActive(planDetails.isActive());

        return planRepository.save(plan);
    }

    public long getActiveMembershipCount(Long planId) {
        return membershipRepository.countActiveMembershipsForPlan(planId, LocalDate.now());
    }
}
