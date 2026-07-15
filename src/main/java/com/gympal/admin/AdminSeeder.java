package com.gympal.admin;

import com.gympal.auth.AppUser;
import com.gympal.auth.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
public class AdminSeeder implements CommandLineRunner {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PlatformAdminRepository platformAdminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        String adminEmail = "admin@fittrack.com";
        Optional<AppUser> existing = appUserRepository.findByEmail(adminEmail);
        
        if (existing.isEmpty()) {
            AppUser adminUser = AppUser.builder()
                    .email(adminEmail)
                    .passwordHash(passwordEncoder.encode("adminpassword123"))
                    .role("SUPER_ADMIN")
                    .build();
            appUserRepository.save(adminUser);

            PlatformAdmin platformAdmin = PlatformAdmin.builder()
                    .authUserId(adminUser.getId())
                    .fullName("Platform Administrator")
                    .build();
            platformAdminRepository.save(platformAdmin);

            System.out.println("=================================================");
            System.out.println("Default Super Admin seeded successfully:");
            System.out.println("Email: " + adminEmail);
            System.out.println("Password: adminpassword123");
            System.out.println("=================================================");
        }
    }
}
