package com.swiftdrop.notification.security;

import com.swiftdrop.common.security.SecurityRules;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Security rules for the notification-service.
 * No public endpoints; all paths protected by internal API key or JWT.
 */
@Component
public class NotificationSecurityRules implements SecurityRules {

    @Override
    public List<String> publicPaths() {
        return List.of();
    }
}
