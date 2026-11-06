package com.swiftdrop.auth.service;

import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.login.service.PasswordPolicy;
import com.swiftdrop.auth.users.bootstrap.BootstrapAdminRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BootstrapAdminRunner Unit Tests")
class BootstrapAdminTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OutboxService outboxService;

    private PasswordEncoder passwordEncoder;
    private PasswordPolicy passwordPolicy;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(12);
        passwordPolicy = new PasswordPolicy();
    }

    @Test
    @DisplayName("Should skip bootstrap creation when admin already exists")
    void whenAdminExists_thenSkipCreation() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(true);

        BootstrapAdminRunner runner = new BootstrapAdminRunner(
                userRepository, passwordEncoder, passwordPolicy, outboxService,
                true, "admin@swiftdrop.lk", "System Admin", "ValidSecret123!"
        );

        runner.run(mock(ApplicationArguments.class));

        verify(userRepository, never()).save(any(User.class));
        verify(outboxService, never()).recordAuditEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should create bootstrap admin successfully with BCrypt and mustChangePassword=true")
    void whenNoAdminExists_thenCreateBootstrapAdmin() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("admin@swiftdrop.lk")).thenReturn(false);

        User savedUser = new User();
        savedUser.setId(UUID.randomUUID());
        savedUser.setEmail("admin@swiftdrop.lk");
        savedUser.setFullName("System Admin");
        savedUser.setRole(UserRole.ADMIN);
        savedUser.setStatus(UserStatus.ACTIVE);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        BootstrapAdminRunner runner = new BootstrapAdminRunner(
                userRepository, passwordEncoder, passwordPolicy, outboxService,
                true, "admin@swiftdrop.lk", "System Admin", "ValidSecret123!"
        );

        runner.run(mock(ApplicationArguments.class));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User created = captor.getValue();
        assertThat(created.getEmail()).isEqualTo("admin@swiftdrop.lk");
        assertThat(created.getFullName()).isEqualTo("System Admin");
        assertThat(created.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(created.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(created.isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches("ValidSecret123!", created.getPasswordHash())).isTrue();

        verify(outboxService).recordAuditEvent(
                eq("BOOTSTRAP_ADMIN_CREATED"),
                eq(savedUser.getId()),
                eq(savedUser.getId()),
                eq("ADMIN"),
                any(Map.class)
        );
    }

    @Test
    @DisplayName("Should do nothing when bootstrap is disabled")
    void whenDisabled_thenDoNothing() {
        BootstrapAdminRunner runner = new BootstrapAdminRunner(
                userRepository, passwordEncoder, passwordPolicy, outboxService,
                false, "admin@swiftdrop.lk", "System Admin", "ValidSecret123!"
        );

        runner.run(mock(ApplicationArguments.class));

        verify(userRepository, never()).existsByRole(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when bootstrap credentials are empty")
    void whenCredentialsMissing_thenThrowException() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);

        BootstrapAdminRunner runner = new BootstrapAdminRunner(
                userRepository, passwordEncoder, passwordPolicy, outboxService,
                true, "", "", ""
        );

        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Bootstrap administrator credentials");
    }

    @Test
    @DisplayName("Should throw when bootstrap admin email is invalid")
    void whenEmailInvalid_thenThrowException() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);

        BootstrapAdminRunner runner = new BootstrapAdminRunner(
                userRepository, passwordEncoder, passwordPolicy, outboxService,
                true, "invalid-email", "System Admin", "ValidSecret123!"
        );

        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid or exceeds 254 characters");
    }

    @Test
    @DisplayName("Should throw when bootstrap admin password does not satisfy complexity")
    void whenPasswordWeak_thenThrowException() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);

        BootstrapAdminRunner runner = new BootstrapAdminRunner(
                userRepository, passwordEncoder, passwordPolicy, outboxService,
                true, "admin@swiftdrop.lk", "System Admin", "weakpass"
        );

        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("complexity requirements");
    }

    @Test
    @DisplayName("Should throw when password contains email local part")
    void whenPasswordContainsEmailLocalPart_thenThrowException() {
        when(userRepository.existsByRole(UserRole.ADMIN)).thenReturn(false);

        BootstrapAdminRunner runner = new BootstrapAdminRunner(
                userRepository, passwordEncoder, passwordPolicy, outboxService,
                true, "admin@swiftdrop.lk", "System Admin", "Admin!Swiftdrop12345"
        );

        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not contain the email address username");
    }
}
