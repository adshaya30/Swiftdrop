package com.swiftdrop.auth;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.RefreshToken;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.RefreshTokenRepository;
import com.swiftdrop.auth.login.service.JwtService;
import com.swiftdrop.auth.login.service.LoginService;
import com.swiftdrop.auth.login.service.RefreshTokenService;
import com.swiftdrop.auth.login.service.PasswordHasher;
import com.swiftdrop.auth.login.service.PasswordPolicy;
import com.swiftdrop.auth.login.controller.AuthController;

import org.junit.jupiter.api.Test;

/**
 * Placeholder test class.
 * Real integration tests are in RegistrationControllerIT (uses Testcontainers).
 */
class AuthApplicationTests {

    @Test
    void placeholder() {
        // no-op; context loading is validated by RegistrationControllerIT
    }
}
