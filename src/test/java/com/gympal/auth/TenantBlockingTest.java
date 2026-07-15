package com.gympal.auth;

import com.gympal.common.exceptions.NotFoundException;
import com.gympal.plans.Plan;
import com.gympal.plans.PlanRepository;
import com.gympal.plans.PlanService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
public class TenantBlockingTest {

    @Autowired
    private PlanService planService;

    @MockBean
    private PlanRepository planRepository;

    @Test
    public void testCrossTenantAccessIsBlocked() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        Long planId = 123L;

        Plan planB = Plan.builder()
                .id(planId)
                .planName("Gold Plan")
                .durationDays(30)
                .price(new BigDecimal("1000"))
                .gymOwnerId(tenantB)
                .build();

        // Mock repo: return plan for tenantB, and empty/blocked for tenantA
        when(planRepository.findByIdAndGymOwnerId(planId, tenantB)).thenReturn(Optional.of(planB));
        when(planRepository.findByIdAndGymOwnerId(planId, tenantA)).thenReturn(Optional.empty());

        // Under tenant B, retrieval succeeds
        Plan fetched = planService.getPlanById(planId, tenantB);
        Assertions.assertNotNull(fetched);
        Assertions.assertEquals(tenantB, fetched.getGymOwnerId());

        // Under tenant A, retrieval fails with NotFoundException (blocked)
        Assertions.assertThrows(NotFoundException.class, () -> {
            planService.getPlanById(planId, tenantA);
        });
    }
}
