package com.gympal.auth;

import com.gympal.common.exceptions.DuplicateEmailException;
import com.gympal.common.exceptions.DuplicatePhoneException;
import com.gympal.gym.GymOwner;
import com.gympal.gym.GymOwnerRepository;
import com.gympal.gym.TrustedIpService;
import com.gympal.admin.AdminSeeder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class SignupRollbackTest {

    @Autowired
    private AuthService authService;

    @MockBean
    private AppUserRepository appUserRepository;

    @MockBean
    private GymOwnerRepository gymOwnerRepository;

    @MockBean
    private TrustedIpService trustedIpService;

    @MockBean
    private AdminSeeder adminSeeder;

    @Test
    public void testSuccessfulSignup() {
        when(appUserRepository.findByEmail("newuser@gympal.com")).thenReturn(Optional.empty());
        when(gymOwnerRepository.findByMobileNumber("9988776655")).thenReturn(Optional.empty());
        
        AppUser savedUser = AppUser.builder().id(UUID.randomUUID()).email("newuser@gympal.com").role("OWNER").build();
        when(appUserRepository.save(any(AppUser.class))).thenReturn(savedUser);

        AuthService.SignupRequest request = new AuthService.SignupRequest();
        request.setEmail("newuser@gympal.com");
        request.setPassword("password123");
        request.setGymName("Iron Muscle");
        request.setOwnerName("John Doe");
        request.setMobile("9988776655");

        AuthService.AuthResponse response = authService.register(request);
        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response.getAccessToken());
        
        verify(appUserRepository, times(1)).save(any(AppUser.class));
        verify(gymOwnerRepository, times(1)).save(any(GymOwner.class));
        verify(trustedIpService, times(1)).saveTrustedIp(any(GymOwner.class), any(String.class), any(String.class));
    }

    @Test
    public void testDuplicateEmailThrowsDuplicateEmailException() {
        AppUser existingUser = AppUser.builder().id(UUID.randomUUID()).email("duplicate@gympal.com").build();
        when(appUserRepository.findByEmail("duplicate@gympal.com")).thenReturn(Optional.of(existingUser));

        AuthService.SignupRequest request = new AuthService.SignupRequest();
        request.setEmail("duplicate@gympal.com");
        request.setPassword("password123");
        request.setGymName("Steel Gym");
        request.setOwnerName("Jane Doe");
        request.setMobile("1122334455");

        Assertions.assertThrows(DuplicateEmailException.class, () -> {
            authService.register(request);
        });
        
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    @Test
    public void testDuplicateMobileThrowsDuplicatePhoneException() {
        when(appUserRepository.findByEmail("user2@gympal.com")).thenReturn(Optional.empty());
        GymOwner existingOwner = GymOwner.builder().id(UUID.randomUUID()).mobileNumber("9876543210").build();
        when(gymOwnerRepository.findByMobileNumber("9876543210")).thenReturn(Optional.of(existingOwner));

        AuthService.SignupRequest request = new AuthService.SignupRequest();
        request.setEmail("user2@gympal.com");
        request.setPassword("password123");
        request.setGymName("Steel Gym");
        request.setOwnerName("Jane Doe");
        request.setMobile("9876543210");

        Assertions.assertThrows(DuplicatePhoneException.class, () -> {
            authService.register(request);
        });
        
        verify(appUserRepository, never()).save(any(AppUser.class));
    }

    @Test
    public void testSignupSucceedsEvenIfTrustedIpServiceFails() {
        doThrow(new RuntimeException("Database error during IP logging"))
                .when(trustedIpService).saveTrustedIp(any(GymOwner.class), any(String.class), any(String.class));

        when(appUserRepository.findByEmail("robustuser@gympal.com")).thenReturn(Optional.empty());
        when(gymOwnerRepository.findByMobileNumber("5566778899")).thenReturn(Optional.empty());

        AppUser savedUser = AppUser.builder().id(UUID.randomUUID()).email("robustuser@gympal.com").role("OWNER").build();
        when(appUserRepository.save(any(AppUser.class))).thenReturn(savedUser);

        AuthService.SignupRequest request = new AuthService.SignupRequest();
        request.setEmail("robustuser@gympal.com");
        request.setPassword("password123");
        request.setGymName("Iron Muscle");
        request.setOwnerName("John Doe");
        request.setMobile("5566778899");

        AuthService.AuthResponse response = authService.register(request);
        Assertions.assertNotNull(response);
        
        verify(appUserRepository, times(1)).save(any(AppUser.class));
        verify(gymOwnerRepository, times(1)).save(any(GymOwner.class));
    }
}
