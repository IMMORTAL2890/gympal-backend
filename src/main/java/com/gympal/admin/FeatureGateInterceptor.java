package com.gympal.admin;

import com.gympal.common.GymOwnerContext;
import com.gympal.common.exceptions.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.UUID;

@Component
public class FeatureGateInterceptor implements HandlerInterceptor {

    @Autowired
    private GymFeatureService gymFeatureService;

    @Autowired
    private GymOwnerContext gymOwnerContext;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            FeatureGate gate = handlerMethod.getMethodAnnotation(FeatureGate.class);
            if (gate == null) {
                gate = handlerMethod.getBeanType().getAnnotation(FeatureGate.class);
            }

            if (gate != null) {
                if (!gymOwnerContext.hasGymOwnerId()) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "Access denied: Gym context missing");
                }
                UUID gymId = gymOwnerContext.getGymOwnerId();
                boolean enabled = gymFeatureService.isFeatureEnabled(gymId, gate.value());
                if (!enabled) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "Feature '" + gate.value() + "' is not enabled for this gym");
                }
            }
        }
        return true;
    }
}
