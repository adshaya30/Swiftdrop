package com.swiftdrop.parcel.order.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.testsupport.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Tag("TST-032")
class OrderAccessTests {

    private static final TestTokens TOKENS = TestTokens.create();

    private final OrderAccess access = new OrderAccess();
    private final UUID mid = UUID.randomUUID();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("SRS 3.6: a merchant dashboard token may read, write and use the dashboard; the owner is mid")
    void dashboardUser() {
        login(TOKENS.merchant(mid).build());

        assertThat(access.requireRead()).isEqualTo(mid);
        assertThat(access.requireWrite()).isEqualTo(mid);
        assertThat(access.requireDashboardUser()).isEqualTo(mid);
    }

    @Test
    @DisplayName("TST-032: a client token needs orders:write to create or cancel and orders:read to read")
    void clientScopes() {
        login(TOKENS.client(mid, "orders:read").build());
        assertThat(access.requireRead()).isEqualTo(mid);
        assertDenied(access::requireWrite);

        login(TOKENS.client(mid, "orders:write").build());
        assertThat(access.requireWrite()).isEqualTo(mid);
        assertDenied(access::requireRead);
    }

    @Test
    @DisplayName("FR-PAR-014: the dashboard summary refuses client tokens")
    void dashboardRefusesClient() {
        login(TOKENS.client(mid, "orders:read", "orders:write").build());

        assertDenied(access::requireDashboardUser);
    }

    @Test
    @DisplayName("BR-25: other roles get 403 ACCESS_DENIED")
    void otherRoles() {
        login(TOKENS.customer().build());
        assertDenied(access::requireRead);
        login(TOKENS.admin().build());
        assertDenied(access::requireWrite);
        login(TOKENS.courier().build());
        assertDenied(access::requireDashboardUser);
    }

    private static void login(String token) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(TOKENS.decode(token)));
    }

    private static void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }
}
