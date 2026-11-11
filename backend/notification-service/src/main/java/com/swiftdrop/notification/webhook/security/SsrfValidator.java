package com.swiftdrop.notification.webhook.security;

import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SSRF validator protecting webhook delivery targets (FR-WHK-007, NFR-SEC-013).
 * Validates schemes, credentials, and resolves DNS to detect blocked loopback,
 * private, link-local (cloud metadata), and multicast ranges.
 */
@Component
public class SsrfValidator {

    private final boolean allowPrivateTargets;
    private final boolean allowHttp;

    public SsrfValidator(
            @Value("${notification.webhook.allow-private-targets:false}") boolean allowPrivateTargets,
            @Value("${notification.webhook.allow-http:false}") boolean allowHttp) {
        this.allowPrivateTargets = allowPrivateTargets;
        this.allowHttp = allowHttp;
    }

    /**
     * Validates the given webhook target URL against SSRF restrictions.
     *
     * @param urlString target URL to validate
     * @throws DomainException with VALIDATION_ERROR if the URL or resolved IP violates policy
     */
    public void validateUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Webhook URL must not be empty");
        }
        if (urlString.length() > 500) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Webhook URL must not exceed 500 characters");
        }

        URI uri;
        try {
            uri = URI.create(urlString.trim());
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Malformed webhook URL: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Webhook URL must contain a valid scheme (https://)");
        }

        if ("http".equalsIgnoreCase(scheme)) {
            if (!allowHttp) {
                throw new DomainException(ErrorCode.VALIDATION_ERROR, "Plain HTTP is not allowed; HTTPS is required");
            }
        } else if (!"https".equalsIgnoreCase(scheme)) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Unsupported URL scheme: " + scheme + ". Only HTTPS is allowed");
        }

        if (uri.getUserInfo() != null && !uri.getUserInfo().trim().isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Webhook URL must not contain user credentials");
        }

        String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Webhook URL must contain a valid host");
        }

        host = host.trim();
        // Resolve host and check all associated IP addresses
        InetAddress[] addresses = resolveHost(host);
        if (addresses == null || addresses.length == 0) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Could not resolve host: " + host);
        }

        for (InetAddress address : addresses) {
            InetAddress normalizedAddress = normalizeAddress(address);
            if (isAddressBlocked(normalizedAddress)) {
                throw new DomainException(ErrorCode.VALIDATION_ERROR,
                        "Target host resolves to a restricted or private address: " + address.getHostAddress());
            }
        }
    }

    private InetAddress[] resolveHost(String host) {
        // Strip IPv6 brackets if present
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }

        // Support numeric IPv4 formats (decimal or hex)
        try {
            if (host.matches("^\\d+$")) {
                long val = Long.parseLong(host);
                byte[] bytes = new byte[]{
                        (byte) ((val >> 24) & 0xFF),
                        (byte) ((val >> 16) & 0xFF),
                        (byte) ((val >> 8) & 0xFF),
                        (byte) (val & 0xFF)
                };
                return new InetAddress[]{InetAddress.getByAddress(bytes)};
            } else if (host.toLowerCase().startsWith("0x")) {
                long val = Long.parseLong(host.substring(2), 16);
                byte[] bytes = new byte[]{
                        (byte) ((val >> 24) & 0xFF),
                        (byte) ((val >> 16) & 0xFF),
                        (byte) ((val >> 8) & 0xFF),
                        (byte) (val & 0xFF)
                };
                return new InetAddress[]{InetAddress.getByAddress(bytes)};
            }
        } catch (Exception ignored) {
            // Fall back to standard DNS resolution
        }

        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Could not resolve host: " + host);
        }
    }

    private InetAddress normalizeAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        // Check for IPv4-mapped IPv6 address (::ffff:x.x.x.x)
        if (address instanceof Inet6Address && bytes.length == 16) {
            boolean isMapped = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) {
                    isMapped = false;
                    break;
                }
            }
            if (isMapped && (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF) {
                try {
                    return InetAddress.getByAddress(Arrays.copyOfRange(bytes, 12, 16));
                } catch (UnknownHostException ignored) {
                }
            }
        }
        return address;
    }

    private boolean isAddressBlocked(InetAddress address) {
        byte[] b = address.getAddress();

        // 1. Unconditionally blocked ranges:
        // Any local address (0.0.0.0/8 or ::)
        if (address.isAnyLocalAddress() || (b.length == 4 && (b[0] & 0xFF) == 0)) {
            return true;
        }

        // Multicast addresses (224.0.0.0/4, ff00::/8)
        if (address.isMulticastAddress()) {
            return true;
        }

        // Link-local addresses (169.254.0.0/16, including AWS metadata 169.254.169.254, fe80::/10)
        if (address.isLinkLocalAddress()) {
            return true;
        }
        if (b.length == 4 && (b[0] & 0xFF) == 169 && (b[1] & 0xFF) == 254) {
            return true;
        }
        if (b.length == 16 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xC0) == 0x80) {
            return true;
        }

        // 2. Conditionally blocked (blocked unless WEBHOOK_ALLOW_PRIVATE_TARGETS=true):
        if (!allowPrivateTargets) {
            // Loopback: 127.0.0.0/8 and ::1
            if (address.isLoopbackAddress() || (b.length == 4 && (b[0] & 0xFF) == 127)) {
                return true;
            }

            // Private IPv4 ranges (RFC 1918):
            // 10.0.0.0/8
            if (b.length == 4 && (b[0] & 0xFF) == 10) {
                return true;
            }
            // 172.16.0.0/12
            if (b.length == 4 && (b[0] & 0xFF) == 172 && (b[1] & 0xFF) >= 16 && (b[1] & 0xFF) <= 31) {
                return true;
            }
            // 192.168.0.0/16
            if (b.length == 4 && (b[0] & 0xFF) == 192 && (b[1] & 0xFF) == 168) {
                return true;
            }

            // Unique-local IPv6 (fc00::/7)
            if (b.length == 16 && (b[0] & 0xFE) == 0xFC) {
                return true;
            }

            // General site local address
            if (address.isSiteLocalAddress()) {
                return true;
            }
        }

        return false;
    }
}

