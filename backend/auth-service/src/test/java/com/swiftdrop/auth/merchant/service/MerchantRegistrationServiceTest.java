package com.swiftdrop.auth.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.entity.MerchantStatus;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.login.service.PasswordHasher;
import com.swiftdrop.auth.login.service.PasswordPolicy;
import com.swiftdrop.auth.merchant.dto.RegisterMerchantRequest;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@Tag("TST-029")
class MerchantRegistrationServiceTest {

    @Mock UserRepository userRepository;
    @Mock MerchantRepository merchantRepository;
    @Mock PasswordHasher passwordHasher;

    MerchantRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new MerchantRegistrationService(userRepository, merchantRepository, new PasswordPolicy(), passwordHasher);
    }

    private static RegisterMerchantRequest request(String email, String phone, String password) {
        return new RegisterMerchantRequest("  Lanka Crafts  ", " ", "  Nimal Perera ", email, phone, password,
                " 12 Galle Road ", " Colombo ");
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-016: creates an ACTIVE MERCHANT user and a PENDING merchant with normalised data")
    void createsUserAndPendingMerchant() {
        when(userRepository.existsByEmailIgnoreCase("shop@example.com")).thenReturn(false);
        when(passwordHasher.hash("Str0ng!Pass#1")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        var response = service.register(request("  Shop@Example.com ", "077 123 4567", "Str0ng!Pass#1"));

        assertThat(response.message()).contains("waiting for admin approval");
        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(user.capture());
        assertThat(user.getValue().getEmail()).isEqualTo("shop@example.com");
        assertThat(user.getValue().getPhone()).isEqualTo("+94771234567");
        assertThat(user.getValue().getFullName()).isEqualTo("Nimal Perera");
        assertThat(user.getValue().getRole()).isEqualTo(UserRole.MERCHANT);
        assertThat(user.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getValue().getPasswordHash()).isEqualTo("hashed");

        ArgumentCaptor<Merchant> merchant = ArgumentCaptor.forClass(Merchant.class);
        verify(merchantRepository).save(merchant.capture());
        assertThat(merchant.getValue().getStatus()).isEqualTo(MerchantStatus.PENDING);
        assertThat(merchant.getValue().getBusinessName()).isEqualTo("Lanka Crafts");
        assertThat(merchant.getValue().getBusinessRegistrationNo()).isNull();
        assertThat(merchant.getValue().getAddressLine1()).isEqualTo("12 Galle Road");
        assertThat(merchant.getValue().getCity()).isEqualTo("Colombo");
        assertThat(merchant.getValue().getUserId()).isEqualTo(user.getValue().getId());
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-016: duplicate email gives 409 EMAIL_ALREADY_REGISTERED and creates nothing")
    void duplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("shop@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request("shop@example.com", "0771234567", "Str0ng!Pass#1")))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED));
        verify(userRepository, never()).saveAndFlush(any());
        verify(merchantRepository, never()).save(any());
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-016: a concurrent duplicate (unique constraint) also gives EMAIL_ALREADY_REGISTERED")
    void concurrentDuplicate() {
        when(userRepository.existsByEmailIgnoreCase("shop@example.com")).thenReturn(false);
        when(passwordHasher.hash(any())).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.register(request("shop@example.com", "0771234567", "Str0ng!Pass#1")))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED));
        verify(merchantRepository, never()).save(any());
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-016: invalid phone gives 400 VALIDATION_ERROR on field phone")
    void invalidPhone() {
        assertThatThrownBy(() -> service.register(request("shop@example.com", "12345", "Str0ng!Pass#1")))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(e.errors()).extracting(f -> f.field()).containsExactly("phone");
                });
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-016: a weak password or one containing the email local part is refused")
    void weakPassword() {
        for (String password : new String[] {"alllowercase1!", "Shop@Example1234!"}) {
            assertThatThrownBy(() -> service.register(request("shop@example.com", "0771234567", password)))
                    .isInstanceOfSatisfying(DomainException.class, e -> {
                        assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                        assertThat(e.errors()).isNotEmpty();
                        assertThat(e.errors()).allSatisfy(f -> assertThat(f.field()).isEqualTo("password"));
                    });
        }
        verify(userRepository, never()).saveAndFlush(any());
    }
}
