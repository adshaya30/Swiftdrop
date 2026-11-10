package com.swiftdrop.parcel.domain;

import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.parcel.support.AbstractParcelDatabaseTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import static com.swiftdrop.parcel.support.ParcelFixtures.parcel;
import static org.assertj.core.api.Assertions.*;

class ParcelRepositoryTests extends AbstractParcelDatabaseTest {
    @Autowired ParcelRepository parcels;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;

    @Test
    @Transactional
    @DisplayName("BR-09 SRS 6.3: embedded snapshots, optional fields, fees and defaults round trip")
    void roundTripsCreationSnapshots() {
        UUID customer = UUID.randomUUID();
        Parcel saved = parcels.saveAndFlush(parcel("SD-0000000001", customer, null, null));
        UUID id = saved.getId();
        assertThat(id.version()).isEqualTo(4);
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
        em.clear();
        Parcel loaded = parcels.findByTrackingNumber("SD-0000000001").orElseThrow();
        assertThat(loaded.getId()).isEqualTo(id);
        assertThat(loaded.getCustomerId()).isEqualTo(customer);
        assertThat(loaded.getMerchantId()).isNull();
        assertThat(loaded.getStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(loaded.getStatusSequence()).isEqualTo(1);
        assertThat(loaded.getPickup().getDistrictName()).isEqualTo("Colombo");
        assertThat(loaded.getPickup().getAddressLine2()).isNull();
        assertThat(loaded.getPickup().getPostalCode()).isNull();
        assertThat(loaded.getDropoff().getContactEmail()).isEqualTo("test@example.invalid");
        assertThat(loaded.getDropoff().getPostalCode()).isEqualTo("20000");
        assertThat(loaded.getOriginZone().getCode()).isEqualTo("WESTERN");
        assertThat(loaded.getDestinationZone().getName()).isEqualTo("Central");
        assertThat(loaded.getWeightKg()).isEqualByComparingTo("2.00");
        assertThat(loaded.getFeeBase()).isEqualByComparingTo("500.00");
        assertThat(loaded.getFeeWeight()).isEqualByComparingTo("150.00");
        assertThat(loaded.getFeeSize()).isEqualByComparingTo("150.00");
        assertThat(loaded.getFeeDiscount()).isEqualByComparingTo("0.00");
        assertThat(loaded.getFeeTotal()).isEqualByComparingTo("800.00");
        assertThat(loaded.getCurrency()).isEqualTo("LKR");
        assertThat(loaded.getTerminalAt()).isNull();
        assertThat(loaded.getDescription()).isNull();
        assertThat(loaded.getDeliveryInstructions()).isNull();
    }

    @Test
    @DisplayName("FR-PAR-002: an actual stale write fails optimistic locking")
    void rejectsStaleVersion() {
        Parcel saved = parcels.saveAndFlush(parcel("SD-0000000002", UUID.randomUUID(), null, null));
        var first = emf.createEntityManager();
        var stale = emf.createEntityManager();
        try {
            first.getTransaction().begin();
            stale.getTransaction().begin();
            Parcel current = first.find(Parcel.class, saved.getId());
            Parcel outdated = stale.find(Parcel.class, saved.getId());
            current.setStatusSequence(2);
            first.getTransaction().commit();
            outdated.setStatusSequence(3);
            assertThatThrownBy(stale::flush).isInstanceOf(OptimisticLockException.class);
        } finally {
            if (first.getTransaction().isActive()) first.getTransaction().rollback();
            if (stale.getTransaction().isActive()) stale.getTransaction().rollback();
            first.close();
            stale.close();
            parcels.deleteById(saved.getId());
        }
    }
}
