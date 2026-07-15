package com.gympal.auth;

import com.gympal.common.GymOwnerContext;
import com.gympal.gym.GymOwner;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private GymOwnerContext gymOwnerContext;

    @PostMapping("/auth/signup")
    public ResponseEntity<AuthService.AuthResponse> signup(@RequestBody AuthService.SignupRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AuthService.AuthResponse> login(@RequestBody AuthService.LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<AuthService.AuthResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(authService.refresh(refreshToken));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        // Stateless JWT logout - simply return 204 No Content
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/oauth/google")
    public ResponseEntity<AuthService.AuthResponse> googleOauth(@RequestBody AuthService.GoogleOauthRequest request) {
        return ResponseEntity.ok(authService.googleOauth(request));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthService.MeResponse> getMe() {
        return ResponseEntity.ok(authService.getMe(gymOwnerContext.getAuthUserId()));
    }

    @PostMapping("/gym/setup")
    public ResponseEntity<GymOwner> setupGym(@RequestBody AuthService.GymSetupRequest request, HttpServletRequest servletRequest) {
        String clientIp = getClientIp(servletRequest);
        return ResponseEntity.ok(authService.setupGym(request, gymOwnerContext.getAuthUserId(), clientIp));
    }

    @Autowired
    private com.gympal.admin.GymFeatureService gymFeatureService;

    @PatchMapping("/gym")
    public ResponseEntity<GymOwner> updateGym(@RequestBody AuthService.GymUpdateRequest request) {
        return ResponseEntity.ok(authService.updateGym(request, gymOwnerContext.getAuthUserId()));
    }

    @GetMapping("/gym/features")
    public ResponseEntity<java.util.List<com.gympal.admin.GymFeature>> getMyFeatures() {
        java.util.UUID gymId = gymOwnerContext.getGymOwnerId();
        return ResponseEntity.ok(gymFeatureService.getGymFeatures(gymId));
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
