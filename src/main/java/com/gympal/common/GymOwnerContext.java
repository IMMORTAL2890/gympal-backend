package com.gympal.common;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import java.util.UUID;

@Component
@RequestScope
public class GymOwnerContext {
    private UUID gymOwnerId;
    private UUID authUserId;

    public UUID getGymOwnerId() {
        if (gymOwnerId == null) {
            throw new IllegalStateException("Gym owner ID not resolved for the current request context");
        }
        return gymOwnerId;
    }

    public void setGymOwnerId(UUID gymOwnerId) {
        this.gymOwnerId = gymOwnerId;
    }

    public UUID getAuthUserId() {
        return authUserId;
    }

    public void setAuthUserId(UUID authUserId) {
        this.authUserId = authUserId;
    }

    public boolean hasGymOwnerId() {
        return gymOwnerId != null;
    }
}
