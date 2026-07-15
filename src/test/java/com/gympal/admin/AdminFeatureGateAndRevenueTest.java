package com.gympal.admin;

import com.gympal.admin.AdminService.AdminDashboardStatsDto;
import com.gympal.gym.GymOwner;
import com.gympal.gym.GymOwnerRepository;
import com.gympal.members.MemberRepository;
import com.gympal.memberships.MembershipRepository;
import com.gympal.payments.PaymentTransaction;
import com.gympal.payments.PaymentTransactionRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
public class AdminFeatureGateAndRevenueTest {

    @Autowired
    private GymFeatureService gymFeatureService;

    @Autowired
    private AdminService adminService;

    @MockBean
    private GymFeatureRepository gymFeatureRepository;

    @MockBean
    private FeatureAuditLogRepository featureAuditLogRepository;

    @MockBean
    private GymOwnerRepository gymOwnerRepository;

    @MockBean
    private MemberRepository memberRepository;

    @MockBean
    private MembershipRepository membershipRepository;

    @MockBean
    private PaymentTransactionRepository paymentTransactionRepository;

    @Test
    public void testIsFeatureEnabledDefault() {
        UUID gymId = UUID.randomUUID();
        when(gymFeatureRepository.findById(new GymFeatureId(gymId, GymFeatureKey.ONLINE_PAYMENTS.name())))
                .thenReturn(Optional.empty());

        boolean enabled = gymFeatureService.isFeatureEnabled(gymId, GymFeatureKey.ONLINE_PAYMENTS);
        Assertions.assertTrue(enabled);
    }

    @Test
    public void testIsFeatureDisabled() {
        UUID gymId = UUID.randomUUID();
        GymFeature feature = GymFeature.builder()
                .gymId(gymId)
                .featureKey(GymFeatureKey.SMS_WHATSAPP_NOTIFICATIONS.name())
                .enabled(false)
                .build();

        when(gymFeatureRepository.findById(new GymFeatureId(gymId, GymFeatureKey.SMS_WHATSAPP_NOTIFICATIONS.name())))
                .thenReturn(Optional.of(feature));

        boolean enabled = gymFeatureService.isFeatureEnabled(gymId, GymFeatureKey.SMS_WHATSAPP_NOTIFICATIONS);
        Assertions.assertFalse(enabled);
    }

    @Test
    public void testDashboardStatsCalculations() {
        UUID ownerId = UUID.randomUUID();
        GymOwner gym = GymOwner.builder()
                .id(ownerId)
                .gymName("Iron Forge")
                .ownerName("Owner A")
                .mobileNumber("9999999999")
                .createdAt(Instant.now().minusSeconds(86400 * 5))
                .build();

        PaymentTransaction tx = PaymentTransaction.builder()
                .id(1L)
                .gymOwnerId(ownerId)
                .amount(new BigDecimal("1500.00"))
                .paymentDate(LocalDate.now())
                .build();

        when(gymOwnerRepository.count()).thenReturn(1L);
        when(gymOwnerRepository.findAll()).thenReturn(List.of(gym));
        when(membershipRepository.findAll()).thenReturn(List.of());
        when(memberRepository.findAll()).thenReturn(List.of());
        when(paymentTransactionRepository.findAll()).thenReturn(List.of(tx));

        AdminDashboardStatsDto stats = adminService.getDashboardStats();
        Assertions.assertEquals(1L, stats.getTotalGymsRegistered());
        Assertions.assertEquals(new BigDecimal("1500.00"), stats.getTotalRevenue());
    }
}
