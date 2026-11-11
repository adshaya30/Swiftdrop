package com.swiftdrop.notification.webhook.config;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("TST-014")
@Tag("FR-WHK-005")
class RetryScheduleTest {

    @Test
    @DisplayName("TST-014: Default retry schedule delays for attempts 1 through 5 and limit 6")
    void retryDelays_defaultSchedule() {
        RetrySchedule schedule = new RetrySchedule("PT1M,PT5M,PT30M,PT2H,PT6H", 6);

        assertThat(schedule.getDelayForAttempt(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(schedule.getDelayForAttempt(2)).isEqualTo(Duration.ofMinutes(5));
        assertThat(schedule.getDelayForAttempt(3)).isEqualTo(Duration.ofMinutes(30));
        assertThat(schedule.getDelayForAttempt(4)).isEqualTo(Duration.ofHours(2));
        assertThat(schedule.getDelayForAttempt(5)).isEqualTo(Duration.ofHours(6));

        // Terminal check: max attempts is 6
        assertThat(schedule.isTerminalFailure(1)).isFalse();
        assertThat(schedule.isTerminalFailure(5)).isFalse();
        assertThat(schedule.isTerminalFailure(6)).isTrue();
        assertThat(schedule.isTerminalFailure(7)).isTrue();
    }

    @Test
    @DisplayName("FR-WHK-005: calculateNextAttempt returns next Instant up to attempt 5 and null for attempt 6")
    void calculateNextAttempt_timestamps() {
        RetrySchedule schedule = new RetrySchedule("PT1M,PT5M,PT30M,PT2H,PT6H", 6);
        Instant now = Instant.parse("2026-11-10T12:00:00Z");

        assertThat(schedule.calculateNextAttempt(now, 1))
                .isEqualTo(now.plus(Duration.ofMinutes(1)));
        assertThat(schedule.calculateNextAttempt(now, 2))
                .isEqualTo(now.plus(Duration.ofMinutes(5)));
        assertThat(schedule.calculateNextAttempt(now, 5))
                .isEqualTo(now.plus(Duration.ofHours(6)));
        assertThat(schedule.calculateNextAttempt(now, 6))
                .isNull();
    }
}

