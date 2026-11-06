package com.swiftdrop.common.testapp;

import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.common.security.CurrentUsers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestController
public class TestController {

    public record Body(@NotBlank @Size(max = 10) String name) {
    }

    @GetMapping("/api/v1/public/ping")
    Map<String, String> ping() {
        return Map.of("pong", "ok");
    }

    @GetMapping("/api/v1/secured")
    Map<String, Object> secured() {
        CurrentUser u = CurrentUsers.get();
        return Map.of("userId", u.userId().toString(), "role", u.role().name(), "typ", u.typ(),
                "scopes", u.scopes().stream().sorted().toList());
    }

    @PostMapping("/api/v1/auth/change-password")
    Map<String, String> changePassword() {
        return Map.of("ok", "yes");
    }

    @GetMapping("/api/v1/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    Map<String, String> adminOnly() {
        return Map.of("ok", "yes");
    }

    @GetMapping({"/api/v1/merchant/orders", "/api/v1/merchant/orders/{id}"})
    Map<String, String> orders() {
        return Map.of("ok", "yes");
    }

    @GetMapping("/api/v1/merchant/credentials")
    Map<String, String> credentials() {
        return Map.of("ok", "yes");
    }

    @GetMapping("/api/v1/scoped")
    @PreAuthorize("hasAuthority('SCOPE_orders:write')")
    Map<String, String> scoped() {
        return Map.of("ok", "yes");
    }

    @GetMapping("/api/v1/boom")
    void boom() {
        throw new IllegalStateException("secret internal detail com.swiftdrop.internal.Thing at SQL select *");
    }

    @GetMapping("/api/v1/fee")
    void fee() {
        throw new DomainException(ErrorCode.FEE_CHANGED, "The fee changed", null, Map.of("newFee", "650.00"));
    }

    @GetMapping("/api/v1/optimistic")
    void optimistic() {
        throw new ObjectOptimisticLockingFailureException(Object.class, UUID.randomUUID());
    }

    @GetMapping("/api/v1/toolarge")
    void tooLarge() {
        throw new MaxUploadSizeExceededException(1024);
    }

    @PostMapping("/api/v1/validate")
    Map<String, String> validate(@Valid @RequestBody Body body) {
        return Map.of("name", body.name());
    }

    @GetMapping("/api/v1/request-id")
    Map<String, String> requestId() {
        return Map.of("requestId", String.valueOf(MDC.get("requestId")));
    }

    @GetMapping("/internal/v1/ping")
    Map<String, String> internalPing() {
        return Map.of("internal", "ok");
    }
}
