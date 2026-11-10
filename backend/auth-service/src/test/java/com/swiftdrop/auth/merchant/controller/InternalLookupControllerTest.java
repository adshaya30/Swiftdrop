package com.swiftdrop.auth.merchant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.entity.MerchantStatus;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.merchant.dto.InternalMerchantView;
import com.swiftdrop.auth.merchant.dto.InternalUserContact;
import com.swiftdrop.auth.merchant.mapper.InternalMapper;
import com.swiftdrop.auth.merchant.service.InternalLookupService;
import com.swiftdrop.common.http.ApiHeaders;
import com.swiftdrop.common.security.InternalApiKey;
import com.swiftdrop.common.security.InternalKeyFilter;
import com.swiftdrop.common.web.GlobalExceptionHandler;
import com.swiftdrop.common.web.ProblemWriter;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The internal endpoints with the real shared InternalKeyFilter, controller, service and mapper (repositories are
 * mocked). The filter is applied as in InternalEndpointsConfig. FR-AUTH-022, TST-033 (internal key missing/wrong).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("TST-033")
class InternalLookupControllerTest {

    static final String KEY = "unit-test-internal-key-0123456789-abcdefghij";

    @Mock UserRepository userRepository;
    @Mock MerchantRepository merchantRepository;

    MockMvc mvc;
    User user;
    Merchant merchant;

    @BeforeEach
    void setUp() {
        InternalLookupService service = new InternalLookupService(userRepository, merchantRepository,
                Mappers.getMapper(InternalMapper.class));
        mvc = MockMvcBuilders.standaloneSetup(new InternalLookupController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(new InternalKeyFilter(new InternalApiKey(KEY),
                                new ProblemWriter(JsonMapper.builder().findAndAddModules().build())),
                        "/internal/v1/users/*", "/internal/v1/merchants/*")
                .build();

        user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Nimal Perera");
        user.setEmail("shop@example.com");
        user.setPhone("+94771234567");
        user.setStatus(UserStatus.ACTIVE);
        merchant = new Merchant(user.getId(), "Lanka Crafts", MerchantStatus.APPROVED);
        merchant.setId(UUID.randomUUID());
        merchant.setDiscountPercent(new BigDecimal("12.50"));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(merchantRepository.findById(merchant.getId())).thenReturn(Optional.of(merchant));
    }

    @Test
    @DisplayName("TST-033 FR-AUTH-022: without X-Internal-Key both endpoints answer 401 UNAUTHENTICATED")
    void missingKey() throws Exception {
        for (String path : new String[] {"/internal/v1/users/" + user.getId() + "/contact",
                "/internal/v1/merchants/" + merchant.getId()}) {
            mvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("shop@example.com"))));
        }
    }

    @Test
    @DisplayName("TST-033 FR-AUTH-022: a wrong key, or a bearer token instead of the key, answers 401")
    void wrongKeyOrJwt() throws Exception {
        String path = "/internal/v1/users/" + user.getId() + "/contact";
        mvc.perform(get(path).header(ApiHeaders.INTERNAL_KEY, "wrong-key")).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header(ApiHeaders.INTERNAL_KEY, KEY + "x")).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header(ApiHeaders.INTERNAL_KEY, "")).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer some.jwt.value")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FR-AUTH-022 SRS 7.4: user contact returns exactly {id, fullName, email, phone}")
    void userContact() throws Exception {
        mvc.perform(get("/internal/v1/users/" + user.getId() + "/contact").header(ApiHeaders.INTERNAL_KEY, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.fullName").value("Nimal Perera"))
                .andExpect(jsonPath("$.email").value("shop@example.com"))
                .andExpect(jsonPath("$.phone").value("+94771234567"))
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    @DisplayName("FR-AUTH-022 SRS 7.4: merchant view returns exactly {id, userId, businessName, status, userStatus, discountPercent, contactEmail}")
    void merchantView() throws Exception {
        mvc.perform(get("/internal/v1/merchants/" + merchant.getId()).header(ApiHeaders.INTERNAL_KEY, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(merchant.getId().toString()))
                .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.businessName").value("Lanka Crafts"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.userStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.discountPercent").value(12.5))
                .andExpect(jsonPath("$.contactEmail").value("shop@example.com"))
                .andExpect(jsonPath("$.length()").value(7));
    }

    @Test
    @DisplayName("FR-AUTH-022: the view reflects a suspended merchant and a deactivated user (parcel-service checks these)")
    void reflectsState() throws Exception {
        merchant.setStatus(MerchantStatus.SUSPENDED);
        user.setStatus(UserStatus.DEACTIVATED);
        mvc.perform(get("/internal/v1/merchants/" + merchant.getId()).header(ApiHeaders.INTERNAL_KEY, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.userStatus").value("DEACTIVATED"));
    }

    @Test
    @DisplayName("FR-AUTH-022: unknown and malformed ids give 404 NOT_FOUND")
    void notFound() throws Exception {
        for (String path : new String[] {"/internal/v1/users/" + UUID.randomUUID() + "/contact",
                "/internal/v1/users/not-a-uuid/contact", "/internal/v1/merchants/" + UUID.randomUUID(),
                "/internal/v1/merchants/not-a-uuid"}) {
            mvc.perform(get(path).header(ApiHeaders.INTERNAL_KEY, KEY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Test
    @DisplayName("BR-28: the DTOs keep contact data out of toString() (logs)")
    void noContactDataInToString() {
        assertThat(new InternalUserContact(user.getId(), "Nimal Perera", "shop@example.com", "+94771234567").toString())
                .doesNotContain("Nimal", "shop@example.com", "+9477");
        assertThat(new InternalMerchantView(merchant.getId(), user.getId(), "Lanka Crafts", MerchantStatus.APPROVED,
                UserStatus.ACTIVE, BigDecimal.ZERO, "shop@example.com").toString()).doesNotContain("shop@example.com");
    }
}
