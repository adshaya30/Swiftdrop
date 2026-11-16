package com.swiftdrop.parcel.order.controller;

import static com.swiftdrop.parcel.support.ParcelTestConfiguration.TOKENS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.parcel.create.service.TrackingNumberGenerator;
import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelRepository;
import com.swiftdrop.parcel.support.AbstractParcelDatabaseTest;
import com.swiftdrop.parcel.support.ParcelFixtures;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Tag("TST-031")
@Tag("TST-032")
class ReceiptApiTests extends AbstractParcelDatabaseTest {

    @Autowired MockMvc mvc;
    @Autowired ParcelRepository parcels;
    @Autowired JdbcTemplate jdbc;

    private final UUID customer = UUID.randomUUID();
    private final UUID mid = UUID.randomUUID();
    private final List<UUID> created = new ArrayList<>();

    @AfterEach
    void cleanup() {
        created.forEach(id -> jdbc.update("DELETE FROM parcels WHERE id = ?", id));
    }

    @Test
    @DisplayName("TST-031 AT-30 FR-PAR-010: the owner customer downloads a PDF with the required receipt content")
    void customerReceipt() throws Exception {
        Parcel parcel = save(customer, null);

        var response = receipt(TOKENS.customer().userId(customer).bearer(), parcel.getId())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"SwiftDrop-Receipt-" + parcel.getTrackingNumber() + ".pdf\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();

        String text = text(response.getContentAsByteArray());
        assertThat(text).contains("SwiftDrop", "Delivery Receipt", parcel.getTrackingNumber(), "Issue date",
                "Test sender", "Test pickup", "Colombo", "Test recipient", "Test dropoff", "Kandy", "20000",
                "2.00 kg", "MEDIUM", "LKR 500.00", "LKR 150.00", "LKR 0.00", "LKR 800.00", "Total", "Created");
        assertThat(text).doesNotContain("Delivered at");
    }

    @Test
    @DisplayName("AT-30 FR-PAR-010: a delivered parcel shows its delivery time in Asia/Colombo")
    void deliveredShowsDeliveryTime() throws Exception {
        Parcel parcel = save(customer, null);
        jdbc.update("UPDATE parcels SET status = 'DELIVERED', terminal_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.parse("2026-10-20T08:15:30Z")), parcel.getId());

        String text = text(receipt(TOKENS.admin().bearer(), parcel.getId()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());

        // 08:15 UTC is 1:45 PM in Asia/Colombo (UTC+05:30).
        assertThat(text).contains("Delivered", "Delivered at", "20 Oct 2026, 1:45 PM");
    }

    @Test
    @DisplayName("NFR-SEC-007 FR-PAR-010: user text is printed as plain text, not interpreted")
    void userTextIsPlain() throws Exception {
        Parcel parcel = ParcelFixtures.parcel(new TrackingNumberGenerator().generate(), customer, null, null);
        jdbc.update("UPDATE parcels SET description = ? WHERE id = ?", "<b>Books</b> & <script>x</script>",
                parcels.saveAndFlush(parcel).getId());
        created.add(parcel.getId());

        String text = text(receipt(TOKENS.customer().userId(customer).bearer(), parcel.getId())
                .andReturn().getResponse().getContentAsByteArray());

        assertThat(text).contains("<b>Books</b> & <script>x</script>");
    }

    @Test
    @DisplayName("TST-031 BR-25: another customer gets 404; the admin gets any receipt")
    void customerOwnership() throws Exception {
        Parcel parcel = save(customer, null);

        receipt(TOKENS.customer().bearer(), parcel.getId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        receipt(TOKENS.admin().bearer(), parcel.getId()).andExpect(status().isOk());
        receipt(TOKENS.admin().bearer(), UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-PAR-010: the owner merchant (dashboard login) gets its receipt; another merchant gets 404")
    void merchantOwnership() throws Exception {
        Parcel parcel = save(null, mid);

        receipt(TOKENS.merchant(mid).bearer(), parcel.getId()).andExpect(status().isOk());
        receipt(TOKENS.merchant(UUID.randomUUID()).bearer(), parcel.getId()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TST-031 FR-PAR-017: a COURIER gets 403 before ownership is checked, even for an unknown parcel")
    void courierForbidden() throws Exception {
        Parcel parcel = save(customer, null);

        receipt(TOKENS.courier().bearer(), parcel.getId()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        receipt(TOKENS.courier().bearer(), UUID.randomUUID()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TST-032: a client-credentials token gets 403 on the receipt; no token gets 401")
    void clientTokenAndAnonymous() throws Exception {
        Parcel parcel = save(null, mid);

        receipt(TOKENS.client(mid, "orders:read", "orders:write").bearer(), parcel.getId())
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/parcels/" + parcel.getId() + "/receipt")).andExpect(status().isUnauthorized());
    }

    private Parcel save(UUID customerId, UUID merchantId) {
        Parcel parcel = parcels.saveAndFlush(ParcelFixtures.parcel(new TrackingNumberGenerator().generate(),
                customerId, merchantId, null));
        created.add(parcel.getId());
        return parcel;
    }

    private ResultActions receipt(String bearer, UUID id) throws Exception {
        return mvc.perform(get("/api/v1/parcels/" + id + "/receipt").header("Authorization", bearer));
    }

    private static String text(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            StringBuilder text = new StringBuilder();
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        } finally {
            reader.close();
        }
    }
}
