package com.swiftdrop.notification.webhook.security;

import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("TST-014")
@Tag("FR-WHK-007")
@Tag("AT-36")
class SsrfValidatorTest {

    @Test
    @DisplayName("TST-014 / AT-36: Link-local cloud metadata (169.254.169.254) is ALWAYS blocked, even with demo flags")
    void validateUrl_cloudMetadata_alwaysBlocked() {
        // With demo flags ON
        SsrfValidator permissive = new SsrfValidator(true, true);
        assertThatThrownBy(() -> permissive.validateUrl("http://169.254.169.254/latest/meta-data"))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    DomainException de = (DomainException) e;
                    org.assertj.core.api.Assertions.assertThat(de.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                });

        // With strict flags
        SsrfValidator strict = new SsrfValidator(false, false);
        assertThatThrownBy(() -> strict.validateUrl("https://169.254.169.254/webhook"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("TST-014: IPv4-mapped IPv6 cloud metadata (::ffff:169.254.169.254) is blocked")
    void validateUrl_ipv4MappedIpv6Metadata_blocked() {
        SsrfValidator permissive = new SsrfValidator(true, true);
        assertThatThrownBy(() -> permissive.validateUrl("http://[::ffff:169.254.169.254]/webhook"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("TST-014: Loopback addresses (127.0.0.1, ::1) blocked when allowPrivateTargets=false, allowed when true")
    void validateUrl_loopback_conditional() {
        SsrfValidator strict = new SsrfValidator(false, true);
        assertThatThrownBy(() -> strict.validateUrl("http://127.0.0.1:9000/webhook"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("restricted or private address");

        assertThatThrownBy(() -> strict.validateUrl("http://[::1]:9000/webhook"))
                .isInstanceOf(DomainException.class);

        SsrfValidator permissive = new SsrfValidator(true, true);
        assertThatCode(() -> permissive.validateUrl("http://127.0.0.1:9000/webhook"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TST-014: Private RFC 1918 ranges blocked when allowPrivateTargets=false, allowed when true")
    void validateUrl_privateRanges_conditional() {
        SsrfValidator strict = new SsrfValidator(false, true);

        // 10.0.0.0/8
        assertThatThrownBy(() -> strict.validateUrl("http://10.1.2.3:8080/hook"))
                .isInstanceOf(DomainException.class);

        // 172.16.0.0/12
        assertThatThrownBy(() -> strict.validateUrl("http://172.20.0.5:8080/hook"))
                .isInstanceOf(DomainException.class);

        // 192.168.0.0/16
        assertThatThrownBy(() -> strict.validateUrl("http://192.168.1.100:8080/hook"))
                .isInstanceOf(DomainException.class);

        // When allowPrivateTargets=true
        SsrfValidator permissive = new SsrfValidator(true, true);
        assertThatCode(() -> permissive.validateUrl("http://192.168.1.100:8080/hook"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TST-014: URL containing credentials (userinfo) is always rejected")
    void validateUrl_credentials_rejected() {
        SsrfValidator validator = new SsrfValidator(true, true);
        assertThatThrownBy(() -> validator.validateUrl("http://user:password@localhost:9000/webhook"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("user credentials");
    }

    @Test
    @DisplayName("TST-014: Plain HTTP rejected when allowHttp=false, allowed when true")
    void validateUrl_httpScheme_conditional() {
        SsrfValidator strict = new SsrfValidator(true, false);
        assertThatThrownBy(() -> strict.validateUrl("http://merchant.example/webhook"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Plain HTTP is not allowed");

        SsrfValidator allowHttpValidator = new SsrfValidator(true, true);
        // Valid external/mock HTTP URL allowed with allowHttp=true
        assertThatCode(() -> allowHttpValidator.validateUrl("http://127.0.0.1:9000/webhook"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TST-014: Numeric host forms (decimal, hex) representing loopback are validated and blocked")
    void validateUrl_numericHosts_validated() {
        // 2130706433 is decimal for 127.0.0.1
        SsrfValidator strict = new SsrfValidator(false, true);
        assertThatThrownBy(() -> strict.validateUrl("http://2130706433/webhook"))
                .isInstanceOf(DomainException.class);

        // 0x7f000001 is hex for 127.0.0.1
        assertThatThrownBy(() -> strict.validateUrl("http://0x7f000001/webhook"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("FR-WHK-007: Valid HTTPS public URLs pass validation")
    void validateUrl_validHttps_passes() {
        SsrfValidator strict = new SsrfValidator(false, false);
        // DNS resolution for well-known domain
        assertThatCode(() -> strict.validateUrl("https://example.com/webhook"))
                .doesNotThrowAnyException();
    }
}

