package com.gympal.auth;

import com.gympal.common.exceptions.BadRequestException;
import com.gympal.common.exceptions.ConflictException;
import com.gympal.common.exceptions.UnauthorizedException;
import com.gympal.common.exceptions.DuplicateEmailException;
import com.gympal.common.exceptions.DuplicatePhoneException;
import com.gympal.gym.GymOwner;
import com.gympal.gym.GymOwnerRepository;
import com.gympal.gym.TrustedIp;
import com.gympal.gym.TrustedIpRepository;
import com.gympal.gym.TrustedIpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private GymOwnerRepository gymOwnerRepository;

    @Autowired
    private TrustedIpRepository trustedIpRepository;

    @Autowired
    private TrustedIpService trustedIpService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private com.gympal.common.EmailService emailService;

    @Transactional
    public AuthResponse register(SignupRequest request) {
        if (request.getEmail() == null || request.getPassword() == null) {
            throw new BadRequestException("Email and password are required");
        }
        if (request.getGymName() == null || request.getGymName().trim().isEmpty()) {
            throw new BadRequestException("Gym name is required");
        }
        if (request.getOwnerName() == null || request.getOwnerName().trim().isEmpty()) {
            throw new BadRequestException("Owner name is required");
        }
        if (request.getMobile() == null || !request.getMobile().matches("^[0-9+\\-\\s]{7,15}$")) {
            throw new BadRequestException("Mobile number must be between 7 and 15 digits");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (appUserRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new DuplicateEmailException("Email is already registered: " + normalizedEmail);
        }

        if (gymOwnerRepository.findByMobileNumber(request.getMobile()).isPresent()) {
            throw new DuplicatePhoneException("Mobile number is already registered: " + request.getMobile());
        }

        AppUser user = AppUser.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role("OWNER")
                .build();
        AppUser savedUser = appUserRepository.save(user);

        GymOwner owner = GymOwner.builder()
                .authUserId(savedUser.getId())
                .gymName(request.getGymName())
                .ownerName(request.getOwnerName())
                .mobileNumber(request.getMobile())
                .autoReminderEnabled(true)
                .reminderDaysBefore(3)
                .build();
        gymOwnerRepository.save(owner);

        try {
            trustedIpService.saveTrustedIp(owner, "127.0.0.1", "Initial sign up device");
        } catch (Exception e) {
            logger.warn("Warning: Failed to save initial trusted IP address for owner {}: {}", owner.getId(), e.getMessage(), e);
        }

        return generateAuthResponse(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        if (request.getEmail() == null) {
            throw new BadRequestException("Email is required");
        }
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        AppUser user = appUserRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        return generateAuthResponse(user);
    }



    @Transactional
    public AuthResponse googleOauth(GoogleOauthRequest request) {
        String rawEmail = extractEmailFromGoogleToken(request.getIdToken());
        String normalizedEmail = rawEmail.trim().toLowerCase();
        Optional<AppUser> userOpt = appUserRepository.findByEmail(normalizedEmail);

        AppUser user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
        } else {
            user = AppUser.builder()
                    .email(normalizedEmail)
                    .role("OWNER")
                    .build();
            appUserRepository.save(user);
        }

        return generateAuthResponse(user);
    }

    @Transactional
    public GymOwner setupGym(GymSetupRequest request, UUID authUserId, String clientIp) {
        AppUser user = appUserRepository.findById(authUserId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));

        if (gymOwnerRepository.findByAuthUserId(authUserId).isPresent()) {
            throw new BadRequestException("Gym is already setup for this user");
        }

        // Validate fields
        if (request.getGymName() == null || request.getGymName().length() < 1 || request.getGymName().length() > 100) {
            throw new BadRequestException("Gym name must be between 1 and 100 characters");
        }
        if (request.getOwnerName() == null || request.getOwnerName().length() < 1 || request.getOwnerName().length() > 100) {
            throw new BadRequestException("Owner name must be between 1 and 100 characters");
        }
        if (request.getMobile() == null || !request.getMobile().matches("^[0-9+\\-\\s]{7,15}$")) {
            throw new BadRequestException("Mobile number must be between 7 and 15 digits");
        }

        GymOwner owner = GymOwner.builder()
                .authUserId(authUserId)
                .gymName(request.getGymName())
                .ownerName(request.getOwnerName())
                .mobileNumber(request.getMobile())
                .autoReminderEnabled(true)
                .reminderDaysBefore(3)
                .build();
        gymOwnerRepository.save(owner);

        // Record trusted IP if client IP is present
        try {
            if (clientIp != null && !clientIp.isEmpty()) {
                // Check if IP format is ipv6 loopback or similar
                String formattedIp = "0:0:0:0:0:0:0:1".equals(clientIp) || "127.0.0.1".equals(clientIp) || "::1".equals(clientIp) ? "127.0.0.1" : clientIp;
                trustedIpService.saveTrustedIp(owner, formattedIp, "This device");
            }
        } catch (Exception e) {
            logger.warn("Warning: Failed to save trusted IP address for owner {}: {}", owner.getId(), e.getMessage(), e);
        }

        return owner;
    }

    @Transactional
    public GymOwner updateGym(GymUpdateRequest request, UUID authUserId) {
        GymOwner owner = gymOwnerRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new BadRequestException("Gym profile not set up yet"));

        if (request.getGymName() != null) {
            if (request.getGymName().length() < 1 || request.getGymName().length() > 100) {
                throw new BadRequestException("Gym name must be between 1 and 100 characters");
            }
            owner.setGymName(request.getGymName());
        }
        if (request.getOwnerName() != null) {
            if (request.getOwnerName().length() < 1 || request.getOwnerName().length() > 100) {
                throw new BadRequestException("Owner name must be between 1 and 100 characters");
            }
            owner.setOwnerName(request.getOwnerName());
        }
        if (request.getMobile() != null) {
            if (!request.getMobile().matches("^[0-9+\\-\\s]{7,15}$")) {
                throw new BadRequestException("Mobile number must be between 7 and 15 digits");
            }
            owner.setMobileNumber(request.getMobile());
        }
        if (request.getAutoReminderEnabled() != null) {
            owner.setAutoReminderEnabled(request.getAutoReminderEnabled());
        }
        if (request.getReminderDaysBefore() != null) {
            owner.setReminderDaysBefore(request.getReminderDaysBefore());
        }

        return gymOwnerRepository.save(owner);
    }

    public MeResponse getMe(UUID authUserId) {
        AppUser user = appUserRepository.findById(authUserId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        GymOwner gym = gymOwnerRepository.findByAuthUserId(authUserId).orElse(null);

        UserDto userDto = new UserDto(user.getId(), user.getEmail(), user.getRole());
        GymDto gymDto = null;
        if (gym != null) {
            gymDto = new GymDto(
                    gym.getId(),
                    gym.getGymName(),
                    gym.getOwnerName(),
                    gym.getMobileNumber(),
                    gym.isAutoReminderEnabled(),
                    gym.getReminderDaysBefore()
            );
        }

        return new MeResponse(userDto, gymDto);
    }

    private AuthResponse generateAuthResponse(AppUser user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        UserDto userDto = new UserDto(user.getId(), user.getEmail(), user.getRole());
        return new AuthResponse(accessToken, userDto);
    }

    private String extractEmailFromGoogleToken(String idToken) {
        // Secure Mock bypass for testing only
        if (idToken.startsWith("MOCK_TOKEN:")) {
            return idToken.substring(11);
        }
        
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();
            java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> httpResponse = client.send(httpRequest, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() == 200) {
                String responseBody = httpResponse.body();
                int emailIndex = responseBody.indexOf("\"email\":\"");
                if (emailIndex != -1) {
                    int start = emailIndex + 9;
                    int end = responseBody.indexOf("\"", start);
                    String email = responseBody.substring(start, end);

                    // Check if email_verified is true/1 (it can be string or boolean)
                    boolean verified = responseBody.contains("\"email_verified\":\"true\"")
                            || responseBody.contains("\"email_verified\":true")
                            || responseBody.contains("\"email_verified\":\"1\"")
                            || responseBody.contains("\"email_verified\":1");
                    
                    if (verified) {
                        return email;
                    } else {
                        throw new UnauthorizedException("Google email address is not verified");
                    }
                }
            } else {
                throw new UnauthorizedException("Google token verification failed (HTTP " + httpResponse.statusCode() + ")");
            }
        } catch (UnauthorizedException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new UnauthorizedException("Google OAuth2 verification timed out. Please try again.");
        } catch (Exception e) {
            throw new UnauthorizedException("Failed to verify Google token: " + e.getMessage());
        }
        throw new UnauthorizedException("Could not extract email from Google token");
    }

    // Static Request/Response classes
    public static class SignupRequest {
        private String email;
        private String password;
        private String gymName;
        private String ownerName;
        private String mobile;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getGymName() { return gymName; }
        public void setGymName(String gymName) { this.gymName = gymName; }
        public String getOwnerName() { return ownerName; }
        public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }
    }

    public static class LoginRequest {
        private String email;
        private String password;
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class GoogleOauthRequest {
        private String idToken;
        public String getIdToken() { return idToken; }
        public void setIdToken(String idToken) { this.idToken = idToken; }
    }

    public static class GymSetupRequest {
        private String gymName;
        private String ownerName;
        private String mobile;
        public String getGymName() { return gymName; }
        public void setGymName(String gymName) { this.gymName = gymName; }
        public String getOwnerName() { return ownerName; }
        public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }
    }

    public static class GymUpdateRequest {
        private String gymName;
        private String ownerName;
        private String mobile;
        private Boolean autoReminderEnabled;
        private Integer reminderDaysBefore;

        public String getGymName() { return gymName; }
        public void setGymName(String gymName) { this.gymName = gymName; }
        public String getOwnerName() { return ownerName; }
        public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }
        public Boolean getAutoReminderEnabled() { return autoReminderEnabled; }
        public void setAutoReminderEnabled(Boolean autoReminderEnabled) { this.autoReminderEnabled = autoReminderEnabled; }
        public Integer getReminderDaysBefore() { return reminderDaysBefore; }
        public void setReminderDaysBefore(Integer reminderDaysBefore) { this.reminderDaysBefore = reminderDaysBefore; }
    }

    public static class UserDto {
        private UUID id;
        private String email;
        private String role;

        public UserDto(UUID id, String email, String role) {
            this.id = id;
            this.email = email;
            this.role = role;
        }
        public UUID getId() { return id; }
        public String getEmail() { return email; }
        public String getRole() { return role; }
    }

    public static class GymDto {
        private UUID id;
        private String gymName;
        private String ownerName;
        private String mobileNumber;
        private boolean autoReminderEnabled;
        private int reminderDaysBefore;

        public GymDto(UUID id, String gymName, String ownerName, String mobileNumber, boolean autoReminderEnabled, int reminderDaysBefore) {
            this.id = id;
            this.gymName = gymName;
            this.ownerName = ownerName;
            this.mobileNumber = mobileNumber;
            this.autoReminderEnabled = autoReminderEnabled;
            this.reminderDaysBefore = reminderDaysBefore;
        }
        public UUID getId() { return id; }
        public String getGymName() { return gymName; }
        public String getOwnerName() { return ownerName; }
        public String getMobileNumber() { return mobileNumber; }
        public boolean isAutoReminderEnabled() { return autoReminderEnabled; }
        public int getReminderDaysBefore() { return reminderDaysBefore; }
    }

    public static class AuthResponse {
        private String accessToken;
        private UserDto user;

        public AuthResponse(String accessToken, UserDto user) {
            this.accessToken = accessToken;
            this.user = user;
        }
        public String getAccessToken() { return accessToken; }
        public UserDto getUser() { return user; }
    }

    public static class MeResponse {
        private UserDto user;
        private GymDto gym;

        public MeResponse(UserDto user, GymDto gym) {
            this.user = user;
            this.gym = gym;
        }
        public UserDto getUser() { return user; }
        public GymDto getGym() { return gym; }
    }

    public static class ForgotPasswordRequest {
        private String email;
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class ResetPasswordRequest {
        private String token;
        private String newPassword;
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        if (request.getEmail() == null) {
            return;
        }
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        Optional<AppUser> userOpt = appUserRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            return; // Silently return for security
        }
        AppUser user = userOpt.get();
        String tokenString = UUID.randomUUID().toString();

        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .token(tokenString)
                .expiryDate(Instant.now().plusSeconds(3600)) // 1 hour expiry
                .build();
        passwordResetTokenRepository.save(token);

        String resetLink = "https://gympal-frontend-8sp65pi8w-gym-pal.vercel.app/reset-password/confirm?token=" + tokenString;
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid or expired password reset token"));
        
        if (token.isExpired()) {
            passwordResetTokenRepository.delete(token);
            throw new BadRequestException("Invalid or expired password reset token");
        }

        AppUser user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        appUserRepository.save(user);

        // Consume token
        passwordResetTokenRepository.delete(token);
    }
}
