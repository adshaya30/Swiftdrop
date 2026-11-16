package com.swiftdrop.auth.merchant.controller;

import com.swiftdrop.auth.merchant.dto.OAuthErrorResponse;
import com.swiftdrop.auth.merchant.dto.OAuthTokenResponse;
import com.swiftdrop.auth.merchant.service.OAuthException;
import com.swiftdrop.auth.merchant.service.OAuthTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.Base64;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/oauth/token: public, form-encoded client-credentials grant (FR-AUTH-019). Errors use the RFC 6749
 * format {error, error_description}, so this controller has its own handler, which takes priority over the
 * service-wide Problem Details handler.
 */
@RestController
@Tag(name = "Merchant API access")
public class OAuthTokenController {

    private static final List<String> SINGLE_VALUE_PARAMS = List.of("grant_type", "client_id", "client_secret", "scope");

    private final OAuthTokenService service;

    public OAuthTokenController(OAuthTokenService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/v1/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtain an access token (client_credentials; client auth in the body or HTTP Basic)")
    public ResponseEntity<OAuthTokenResponse> token(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam MultiValueMap<String, String> params) {

        // RFC 6749 3.2: a parameter must not appear more than once
        for (String name : SINGLE_VALUE_PARAMS) {
            if (params.getOrDefault(name, List.of()).size() > 1) {
                throw OAuthException.invalidRequest("Parameter " + name + " must not be repeated");
            }
        }
        String clientId = params.getFirst("client_id");
        String clientSecret = params.getFirst("client_secret");

        String[] basic = basicCredentials(authorization);
        if (basic != null) {
            if (clientId != null || clientSecret != null) {
                throw OAuthException.invalidRequest("Use either HTTP Basic or the request body for client authentication");
            }
            clientId = basic[0];
            clientSecret = basic[1];
        }

        OAuthTokenResponse response = service.issue(params.getFirst("grant_type"), clientId, clientSecret,
                params.getFirst("scope"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache")
                .body(response);
    }

    @ExceptionHandler(OAuthException.class)
    public ResponseEntity<OAuthErrorResponse> oauthError(OAuthException ex) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.status())
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache");
        if (ex.status() == HttpStatus.UNAUTHORIZED.value()) {
            builder.header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"swiftdrop\"");
        }
        return builder.body(new OAuthErrorResponse(ex.error(), ex.getMessage()));
    }

    /** Returns {id, secret} from a Basic Authorization header, or null when another or no scheme is used. */
    private static String[] basicCredentials(String authorization) {
        if (authorization == null || authorization.length() < 6 || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(authorization.substring(6).trim()),
                    StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon < 1) {
                throw OAuthException.invalidRequest("Malformed Basic credentials");
            }
            // RFC 6749 2.3.1: id and secret are form-urlencoded before Base64
            return new String[] {URLDecoder.decode(decoded.substring(0, colon), StandardCharsets.UTF_8),
                    URLDecoder.decode(decoded.substring(colon + 1), StandardCharsets.UTF_8)};
        } catch (IllegalArgumentException e) {
            throw OAuthException.invalidRequest("Malformed Basic credentials");
        }
    }
}
