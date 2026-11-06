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

import com.swiftdrop.auth.dto.request.ChangePasswordRequest;
import com.swiftdrop.auth.dto.request.UpdateProfileRequest;
import com.swiftdrop.auth.dto.response.UserProfileResponse;
import com.swiftdrop.auth.entity.*;
import com.swiftdrop.auth.exception.*;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.RefreshTokenRepository;
import com.swiftdrop.auth.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("ProfileService Unit Tests")
class ProfileServiceTest {

    private UserRepository userRepository;
    private MerchantRepository merchantRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordEncoder passwordEncoder;
    private ProfileService profileService;

    private UUID userId;
    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        merchantRepository = mock(MerchantRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);

        profileService = new ProfileService(
                userRepository,
                merchantRepository,
                refreshTokenRepository,
                passwordEncoder
        );

        userId = UUID.randomUUID();
        testUser = new User();
        testUser.setId(userId);
        testUser.setFullName("Amal Perera");
        testUser.setEmail("amal.perera@example.lk");
        testUser.setPhone("+94771234567");
        testUser.setPasswordHash("$2a$12$hashedpassword");
        testUser.setRole(UserRole.CUSTOMER);
        testUser.setAuthProvider(AuthProvider.LOCAL);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setMustChangePassword(false);
    }

    // -------------------------------------------------------------------------
    // GET PROFILE (FR-AUTH-012)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getProfile returns customer profile without merchant info")
    void getProfile_customer_success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        UserProfileResponse response = profileService.getProfile(userId);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.fullName()).isEqualTo("Amal Perera");
        assertThat(response.email()).isEqualTo("amal.perera@example.lk");
        assertThat(response.phone()).isEqualTo("+94771234567");
        assertThat(response.role()).isEqualTo(UserRole.CUSTOMER);
        assertThat(response.authProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(response.mustChangePassword()).isFalse();
        assertThat(response.merchant()).isNull();
        verifyNoInteractions(merchantRepository);
    }

    @Test
    @DisplayName("getProfile returns merchant profile with merchant summary")
    void getProfile_merchant_success() {
        testUser.setRole(UserRole.MERCHANT);
        Merchant merchant = new Merchant(userId, "Amal Logistics", MerchantStatus.APPROVED);
        merchant.setId(UUID.randomUUID());

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(merchant));

        UserProfileResponse response = profileService.getProfile(userId);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.role()).isEqualTo(UserRole.MERCHANT);
        assertThat(response.merchant()).isNotNull();
        assertThat(response.merchant().id()).isEqualTo(merchant.getId());
        assertThat(response.merchant().businessName()).isEqualTo("Amal Logistics");
        assertThat(response.merchant().status()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(response.merchant().discountPercent()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getProfile throws UserNotFoundException when user does not exist")
    void getProfile_userNotFound_throwsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getProfile(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(userId.toString());
    }

    // -------------------------------------------------------------------------
    // UPDATE PROFILE (FR-AUTH-012)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("updateProfile successfully modifies fullName and phone")
    void updateProfile_success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest("Amal K. Perera", "+94779876543");
        UserProfileResponse response = profileService.updateProfile(userId, request);

        assertThat(response.fullName()).isEqualTo("Amal K. Perera");
        assertThat(response.phone()).isEqualTo("+94779876543");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFullName()).isEqualTo("Amal K. Perera");
        assertThat(captor.getValue().getPhone()).isEqualTo("+94779876543");
    }

    @Test
    @DisplayName("updateProfile with null or blank phone sets phone to null")
    void updateProfile_blankPhone_setsNull() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest("Amal Perera", "   ");
        UserProfileResponse response = profileService.updateProfile(userId, request);

        assertThat(response.phone()).isNull();
        assertThat(testUser.getPhone()).isNull();
    }

    @Test
    @DisplayName("updateProfile throws UserNotFoundException when user does not exist")
    void updateProfile_userNotFound_throwsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        UpdateProfileRequest request = new UpdateProfileRequest("Amal Perera", "+94771234567");
        assertThatThrownBy(() -> profileService.updateProfile(userId, request))
                .isInstanceOf(UserNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // CHANGE PASSWORD (FR-AUTH-013, FR-AUTH-014)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("changePassword successfully changes password and revokes all refresh tokens")
    void changePassword_success() {
        testUser.setMustChangePassword(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("OldPassword#123", "$2a$12$hashedpassword")).thenReturn(true);
        when(passwordEncoder.encode("NewSecret#2026")).thenReturn("$2a$12$newhashedpassword");
        when(refreshTokenRepository.revokeAllByUserId(eq(userId), any(Instant.class))).thenReturn(2);

        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword#123", "NewSecret#2026");
        profileService.changePassword(userId, request);

        assertThat(testUser.getPasswordHash()).isEqualTo("$2a$12$newhashedpassword");
        assertThat(testUser.isMustChangePassword()).isFalse();
        verify(userRepository).saveAndFlush(testUser);
        verify(refreshTokenRepository).revokeAllByUserId(eq(userId), any(Instant.class));
    }

    @Test
    @DisplayName("changePassword throws SocialAccountPasswordChangeException for Google account")
    void changePassword_googleAccount_throwsException() {
        testUser.setAuthProvider(AuthProvider.GOOGLE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        ChangePasswordRequest request = new ChangePasswordRequest("Current#123", "NewSecret#2026");
        assertThatThrownBy(() -> profileService.changePassword(userId, request))
                .isInstanceOf(SocialAccountPasswordChangeException.class)
                .hasMessageContaining("Social login accounts");

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("changePassword throws SocialAccountPasswordChangeException when passwordHash is null")
    void changePassword_nullPasswordHash_throwsException() {
        testUser.setPasswordHash(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        ChangePasswordRequest request = new ChangePasswordRequest("Current#123", "NewSecret#2026");
        assertThatThrownBy(() -> profileService.changePassword(userId, request))
                .isInstanceOf(SocialAccountPasswordChangeException.class);
    }

    @Test
    @DisplayName("changePassword throws InvalidCurrentPasswordException when current password does not match")
    void changePassword_invalidCurrentPassword_throwsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPassword#1", "$2a$12$hashedpassword")).thenReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest("WrongPassword#1", "NewSecret#2026");
        assertThatThrownBy(() -> profileService.changePassword(userId, request))
                .isInstanceOf(InvalidCurrentPasswordException.class)
                .hasMessageContaining("Current password is incorrect");

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("changePassword throws PasswordSameAsCurrentException when new password equals current password")
    void changePassword_samePassword_throwsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("SameSecret#123", "$2a$12$hashedpassword")).thenReturn(true);

        ChangePasswordRequest request = new ChangePasswordRequest("SameSecret#123", "SameSecret#123");
        assertThatThrownBy(() -> profileService.changePassword(userId, request))
                .isInstanceOf(PasswordSameAsCurrentException.class)
                .hasMessageContaining("must be different");

        verifyNoInteractions(refreshTokenRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short#1A",            // Less than 10 characters
            "lowercaseandnumbers123#", // Missing uppercase
            "UPPERCASEANDNUMBERS123#", // Missing lowercase
            "NoSpecialCharacter1234",  // Missing special char
            "NoDigitsHereAtAll!#$",    // Missing digit
    })
    @DisplayName("changePassword throws PasswordPolicyException for passwords violating complexity")
    void changePassword_violatingComplexity_throwsException(String invalidPassword) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Current#123", "$2a$12$hashedpassword")).thenReturn(true);

        ChangePasswordRequest request = new ChangePasswordRequest("Current#123", invalidPassword);
        assertThatThrownBy(() -> profileService.changePassword(userId, request))
                .isInstanceOf(PasswordPolicyException.class)
                .hasMessageContaining("Password must be 10–64 characters");
    }

    @Test
    @DisplayName("changePassword throws PasswordPolicyException when new password contains email username")
    void changePassword_containsEmailUsername_throwsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Current#123", "$2a$12$hashedpassword")).thenReturn(true);

        // email is amal.perera@example.lk -> local-part is "amal.perera"
        ChangePasswordRequest request = new ChangePasswordRequest("Current#123", "SecretAmal.Perera#99");
        assertThatThrownBy(() -> profileService.changePassword(userId, request))
                .isInstanceOf(PasswordPolicyException.class)
                .hasMessageContaining("email address");
    }
}
