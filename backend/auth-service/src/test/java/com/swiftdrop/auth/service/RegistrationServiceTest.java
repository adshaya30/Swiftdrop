package com.swiftdrop.auth.service;

import com.swiftdrop.auth.exception.InvalidPhoneException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swiftdrop.auth.exception.PasswordPolicyException;
import com.swiftdrop.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link RegistrationService} phone normalisation and password validation logic.
 * These tests run without Spring context or database.
 */
@DisplayName("RegistrationService Unit Tests")
class RegistrationServiceTest {

    private final UserRepository userRepo = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final RegistrationService service = new RegistrationService(userRepo, encoder);

    // -----------------------------------------------------------------------
    // Phone normalisation – valid inputs
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Local 07X format normalised to +94")
    void localFormat_normalised() {
        assertThat(service.normalisePhone("0771234567")).isEqualTo("+94771234567");
    }

    @Test
    @DisplayName("+94 prefix already E.164 is accepted")
    void plus94Format_accepted() {
        assertThat(service.normalisePhone("+94771234567")).isEqualTo("+94771234567");
    }

    @Test
    @DisplayName("0094 international prefix normalised")
    void zerozero94Format_normalised() {
        assertThat(service.normalisePhone("0094771234567")).isEqualTo("+94771234567");
    }

    @Test
    @DisplayName("Hyphens stripped from phone")
    void hyphens_stripped() {
        assertThat(service.normalisePhone("077-123-4567")).isEqualTo("+94771234567");
    }

    @Test
    @DisplayName("Spaces stripped from phone")
    void spaces_stripped() {
        assertThat(service.normalisePhone("077 123 4567")).isEqualTo("+94771234567");
    }

    // -----------------------------------------------------------------------
    // Phone normalisation – invalid inputs
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "Invalid phone: {0}")
    @ValueSource(strings = {"123456", "+447911123456", "abcdefghij", "", "00000000000"})
    @DisplayName("Invalid phones throw InvalidPhoneException")
    void invalidPhones_throw(String phone) {
        assertThatThrownBy(() -> service.normalisePhone(phone))
                .isInstanceOf(InvalidPhoneException.class);
    }

    // -----------------------------------------------------------------------
    // Password validation
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Valid strong password passes")
    void validPassword_passes() {
        // Should not throw
        service.validatePassword("Str0ng!Pass#1", "test@example.com");
    }

    @Test
    @DisplayName("Password without uppercase throws PasswordPolicyException")
    void noUppercase_throws() {
        assertThatThrownBy(() -> service.validatePassword("str0ng!pass#1", "test@example.com"))
                .isInstanceOf(PasswordPolicyException.class);
    }

    @Test
    @DisplayName("Password without lowercase throws PasswordPolicyException")
    void noLowercase_throws() {
        assertThatThrownBy(() -> service.validatePassword("STR0NG!PASS#1", "test@example.com"))
                .isInstanceOf(PasswordPolicyException.class);
    }

    @Test
    @DisplayName("Password without digit throws PasswordPolicyException")
    void noDigit_throws() {
        assertThatThrownBy(() -> service.validatePassword("Strong!Pass#Abc", "test@example.com"))
                .isInstanceOf(PasswordPolicyException.class);
    }

    @Test
    @DisplayName("Password without special char throws PasswordPolicyException")
    void noSpecialChar_throws() {
        assertThatThrownBy(() -> service.validatePassword("Str0ngPassAbc1", "test@example.com"))
                .isInstanceOf(PasswordPolicyException.class);
    }

    @Test
    @DisplayName("Password shorter than 10 chars throws (caught by @Size before service, but service also guards)")
    void tooShort_throws() {
        assertThatThrownBy(() -> service.validatePassword("Short1!", "test@example.com"))
                .isInstanceOf(PasswordPolicyException.class);
    }

    @Test
    @DisplayName("Password containing email local-part (case-insensitive) throws")
    void passwordContainsEmailLocalPart_throws() {
        assertThatThrownBy(() -> service.validatePassword("Alice!1Password", "alice@example.com"))
                .isInstanceOf(PasswordPolicyException.class);
    }

    @Test
    @DisplayName("Password containing email local-part uppercased throws")
    void passwordContainsEmailLocalPartUpper_throws() {
        assertThatThrownBy(() -> service.validatePassword("ALICE!1Password", "alice@example.com"))
                .isInstanceOf(PasswordPolicyException.class);
    }
}
