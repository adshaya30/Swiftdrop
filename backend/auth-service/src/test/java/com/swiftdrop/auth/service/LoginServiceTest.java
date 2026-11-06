package com.swiftdrop.auth.service;
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

import com.swiftdrop.auth.dto.request.LoginRequest;
import com.swiftdrop.auth.dto.response.LoginResult;
import com.swiftdrop.auth.entity.*;
import com.swiftdrop.auth.exception.*;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.RefreshTokenRepository;
import com.swiftdrop.auth.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginService Unit Tests")
class LoginServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private LoginService loginService;

    private User sampleUser;
    private final UUID userId = UUID.randomUUID();
    private final String rawPassword = "SecurePass123!";
    private final String passwordHash = "$2a$12$hashedPasswordExample";

    @BeforeEach
    void setUp() {
        loginService = new LoginService(
                userRepository,
                merchantRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtService
        );

        sampleUser = new User();
        sampleUser.setId(userId);
        sampleUser.setEmail("kasun.perera@example.lk");
        sampleUser.setFullName("Kasun Perera");
        sampleUser.setRole(UserRole.CUSTOMER);
        sampleUser.setStatus(UserStatus.ACTIVE);
        sampleUser.setPasswordHash(passwordHash);
        sampleUser.setFailedLoginCount(0);
        sampleUser.setLockedUntil(null);
    }

    @Test
    @DisplayName("Correct customer email and password returns valid LoginResult")
    void login_success_returnsResult() {
        when(userRepository.findByEmailIgnoreCase("kasun.perera@example.lk"))
                .thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches(rawPassword, passwordHash)).thenReturn(true);
        when(jwtService.issueAccessToken(eq(userId), eq("CUSTOMER"), eq(false), isNull()))
                .thenReturn("mocked.jwt.token");

        LoginRequest request = new LoginRequest("kasun.perera@example.lk", rawPassword);
        LoginResult result = loginService.login(request, "127.0.0.1", "Mozilla/5.0");

        assertThat(result).isNotNull();
        assertThat(result.getResponse().getAccessToken()).isEqualTo("mocked.jwt.token");
        assertThat(result.getResponse().getTokenType()).isEqualTo("Bearer");
        assertThat(result.getResponse().getExpiresIn()).isEqualTo(900);
        assertThat(result.getResponse().getUser().getId()).isEqualTo(userId);
        assertThat(result.getResponse().getUser().getFullName()).isEqualTo("Kasun Perera");
        assertThat(result.getResponse().getUser().getRole()).isEqualTo("CUSTOMER");
        assertThat(result.getRawRefreshToken()).isNotBlank();

        verify(userRepository).resetFailedLoginLock(eq(userId), any(Instant.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Invalid password records failed attempt and throws InvalidCredentialsException")
    void login_invalidPassword_throws401AndRecordsFailure() {
        when(userRepository.findByEmailIgnoreCase("kasun.perera@example.lk"))
                .thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPass123!", passwordHash)).thenReturn(false);

        LoginRequest request = new LoginRequest("kasun.perera@example.lk", "WrongPass123!");

        assertThatThrownBy(() -> loginService.login(request, "127.0.0.1", "Mozilla/5.0"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(userRepository).recordFailedLoginAttempt(eq(userId), any(Instant.class));
    }

    @Test
    @DisplayName("Unknown email throws InvalidCredentialsException without leaking user existence")
    void login_unknownEmail_throws401() {
        when(userRepository.findByEmailIgnoreCase("unknown@example.lk"))
                .thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("unknown@example.lk", rawPassword);

        assertThatThrownBy(() -> loginService.login(request, "127.0.0.1", "Mozilla/5.0"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("Google-only account with no local password throws InvalidCredentialsException")
    void login_googleOnlyAccount_throws401() {
        sampleUser.setPasswordHash(null);
        sampleUser.setAuthProvider(AuthProvider.GOOGLE);

        when(userRepository.findByEmailIgnoreCase("kasun.perera@example.lk"))
                .thenReturn(Optional.of(sampleUser));

        LoginRequest request = new LoginRequest("kasun.perera@example.lk", rawPassword);

        assertThatThrownBy(() -> loginService.login(request, "127.0.0.1", "Mozilla/5.0"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository).recordFailedLoginAttempt(eq(userId), any(Instant.class));
    }

    @Test
    @DisplayName("Locked account throws AccountLockedException with retry-after seconds even with correct password")
    void login_lockedAccount_rejectsEvenWithCorrectPassword() {
        Instant lockedUntil = Instant.now().plus(10, ChronoUnit.MINUTES);
        sampleUser.setLockedUntil(lockedUntil);
        sampleUser.setFailedLoginCount(5);

        when(userRepository.findByEmailIgnoreCase("kasun.perera@example.lk"))
                .thenReturn(Optional.of(sampleUser));

        LoginRequest request = new LoginRequest("kasun.perera@example.lk", rawPassword);

        assertThatThrownBy(() -> loginService.login(request, "127.0.0.1", "Mozilla/5.0"))
                .isInstanceOf(AccountLockedException.class);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("DEACTIVATED user throws AccountDeactivatedException after correct credentials")
    void login_deactivatedUser_throws403() {
        sampleUser.setStatus(UserStatus.DEACTIVATED);

        when(userRepository.findByEmailIgnoreCase("kasun.perera@example.lk"))
                .thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches(rawPassword, passwordHash)).thenReturn(true);

        LoginRequest request = new LoginRequest("kasun.perera@example.lk", rawPassword);

        assertThatThrownBy(() -> loginService.login(request, "127.0.0.1", "Mozilla/5.0"))
                .isInstanceOf(AccountDeactivatedException.class);
    }

    @Test
    @DisplayName("PENDING merchant throws MerchantPendingException")
    void login_pendingMerchant_throws403() {
        sampleUser.setRole(UserRole.MERCHANT);
        Merchant merchant = new Merchant(userId, "Test Store", MerchantStatus.PENDING);

        when(userRepository.findByEmailIgnoreCase("kasun.perera@example.lk"))
                .thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches(rawPassword, passwordHash)).thenReturn(true);
        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(merchant));

        LoginRequest request = new LoginRequest("kasun.perera@example.lk", rawPassword);

        assertThatThrownBy(() -> loginService.login(request, "127.0.0.1", "Mozilla/5.0"))
                .isInstanceOf(MerchantPendingException.class);
    }

    @Test
    @DisplayName("REJECTED merchant throws MerchantRejectedException")
    void login_rejectedMerchant_throws403() {
        sampleUser.setRole(UserRole.MERCHANT);
        Merchant merchant = new Merchant(userId, "Test Store", MerchantStatus.REJECTED);

        when(userRepository.findByEmailIgnoreCase("kasun.perera@example.lk"))
                .thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches(rawPassword, passwordHash)).thenReturn(true);
        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(merchant));

        LoginRequest request = new LoginRequest("kasun.perera@example.lk", rawPassword);

        assertThatThrownBy(() -> loginService.login(request, "127.0.0.1", "Mozilla/5.0"))
                .isInstanceOf(MerchantRejectedException.class);
    }

    @Test
    @DisplayName("SUSPENDED merchant throws MerchantSuspendedException")
    void login_suspendedMerchant_throws403() {
        sampleUser.setRole(UserRole.MERCHANT);
        Merchant merchant = new Merchant(userId, "Test Store", MerchantStatus.SUSPENDED);

        when(userRepository.findByEmailIgnoreCase("kasun.perera@example.lk"))
                .thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches(rawPassword, passwordHash)).thenReturn(true);
        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(merchant));

        LoginRequest request = new LoginRequest("kasun.perera@example.lk", rawPassword);

        assertThatThrownBy(() -> loginService.login(request, "127.0.0.1", "Mozilla/5.0"))
                .isInstanceOf(MerchantSuspendedException.class);
    }

    @Test
    @DisplayName("APPROVED merchant logs in successfully with merchantId and merchantStatus")
    void login_approvedMerchant_success() {
        sampleUser.setRole(UserRole.MERCHANT);
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(userId, "Test Store", MerchantStatus.APPROVED);
        merchant.setId(merchantId);

        when(userRepository.findByEmailIgnoreCase("kasun.perera@example.lk"))
                .thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches(rawPassword, passwordHash)).thenReturn(true);
        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(merchant));
        when(jwtService.issueAccessToken(eq(userId), eq("MERCHANT"), eq(false), eq(merchantId)))
                .thenReturn("mocked.jwt.merchant.token");

        LoginRequest request = new LoginRequest("kasun.perera@example.lk", rawPassword);
        LoginResult result = loginService.login(request, "127.0.0.1", "Mozilla/5.0");

        assertThat(result.getResponse().getUser().getMerchantStatus()).isEqualTo("APPROVED");
        assertThat(result.getResponse().getUser().getMerchantId()).isEqualTo(merchantId);
    }

    @Test
    @DisplayName("Only SHA-256 hash of refresh token is stored in repository")
    void login_storesSha256HashOfRefreshToken() {
        when(userRepository.findByEmailIgnoreCase("kasun.perera@example.lk"))
                .thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches(rawPassword, passwordHash)).thenReturn(true);
        when(jwtService.issueAccessToken(any(), any(), anyBoolean(), any()))
                .thenReturn("mocked.jwt.token");

        LoginRequest request = new LoginRequest("kasun.perera@example.lk", rawPassword);
        LoginResult result = loginService.login(request, "127.0.0.1", "Mozilla/5.0");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertThat(saved.getTokenHash()).isNotEqualTo(result.getRawRefreshToken());
        assertThat(saved.getTokenHash()).hasSize(64); // SHA-256 hex is 64 characters
    }
}

