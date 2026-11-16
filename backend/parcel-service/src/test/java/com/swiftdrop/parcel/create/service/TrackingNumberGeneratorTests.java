package com.swiftdrop.parcel.create.service;

import java.security.SecureRandom;
import java.util.HashSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("TST-012")
class TrackingNumberGeneratorTests {
    @Test
    void generatesTenThousandDistinctCrockfordNumbers() {
        var generator = new TrackingNumberGenerator();
        var numbers = new HashSet<String>();
        for (int i = 0; i < 10_000; i++) {
            String number = generator.generate();
            assertThat(number).matches("^SD-[0-9A-HJKMNP-TV-Z]{10}$").hasSize(13);
            assertThat(numbers.add(number)).isTrue();
        }
    }

    @Test
    void usesSecureRandomAcrossTheEntireAlphabet() {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(32)).thenReturn(0, 31, 10, 11, 12, 13, 14, 15, 16, 17);
        assertThat(new TrackingNumberGenerator(random).generate()).isEqualTo("SD-0ZABCDEFGH");
        verify(random, times(10)).nextInt(32);
    }

    @Test
    void doesNotClaimToResolveDatabaseCollisions() {
        SecureRandom random = mock(SecureRandom.class);
        var generator = new TrackingNumberGenerator(random);
        assertThat(generator.generate()).isEqualTo("SD-0000000000");
        assertThat(generator.generate()).isEqualTo("SD-0000000000");
    }
}
