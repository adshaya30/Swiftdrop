package com.swiftdrop.notification.webhook.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Calculates exponential retry schedule for webhook deliveries (SRS 9.2, FR-WHK-005).
 * Defaults: delays = PT1M,PT5M,PT30M,PT2H,PT6H; maxAttempts = 6.
 */
@Component
public class RetrySchedule {

    private final List<Duration> delays;
    private final int maxAttempts;

    public RetrySchedule(
            @Value("${notification.webhook.retry-delays:PT1M,PT5M,PT30M,PT2H,PT6H}") String delaysStr,
            @Value("${notification.webhook.max-attempts:6}") int maxAttempts) {
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : 6;
        if (delaysStr == null || delaysStr.trim().isEmpty()) {
            delaysStr = "PT1M,PT5M,PT30M,PT2H,PT6H";
        }
        this.delays = Arrays.stream(delaysStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Duration::parse)
                .toList();
    }

    /**
     * Checks if the delivery has reached or exceeded max attempts (terminal FAILED).
     *
     * @param attemptCount number of completed attempts
     * @return true if no more retries should be scheduled
     */
    public boolean isTerminalFailure(int attemptCount) {
        return attemptCount >= maxAttempts;
    }

    /**
     * Calculates the duration delay for the given failed attempt number (1-based).
     *
     * @param failedAttemptNumber failed attempt number (1 for first failure, 2 for second...)
     * @return Duration delay
     */
    public Duration getDelayForAttempt(int failedAttemptNumber) {
        if (failedAttemptNumber <= 0 || delays.isEmpty()) {
            return Duration.ZERO;
        }
        int index = Math.min(failedAttemptNumber - 1, delays.size() - 1);
        return delays.get(index);
    }

    /**
     * Calculates the next attempt timestamp based on the last attempt and attempt count.
     * Returns null if max attempts have been exceeded.
     *
     * @param lastAttemptTime the timestamp of the last attempt
     * @param attemptCount    the number of completed attempts
     * @return the next attempt Instant, or null if terminal
     */
    public Instant calculateNextAttempt(Instant lastAttemptTime, int attemptCount) {
        if (isTerminalFailure(attemptCount)) {
            return null;
        }
        Duration delay = getDelayForAttempt(attemptCount);
        return lastAttemptTime.plus(delay);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public List<Duration> getDelays() {
        return delays;
    }
}

