package dev.justine.proxyvote.meeting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeadlineStatusTest {
    private static final Instant DEADLINE = Instant.parse("2026-11-10T21:00:00Z");
    private static final Duration WINDOW = Duration.ofHours(48);

    @Test
    void boundaries() {
        assertThat(DeadlineStatus.of(DEADLINE, DEADLINE.minus(Duration.ofHours(49)), WINDOW)).isEqualTo(DeadlineStatus.OPEN);
        assertThat(DeadlineStatus.of(DEADLINE, DEADLINE.minus(WINDOW), WINDOW)).isEqualTo(DeadlineStatus.CLOSING_SOON);
        assertThat(DeadlineStatus.of(DEADLINE, DEADLINE.minusSeconds(1), WINDOW)).isEqualTo(DeadlineStatus.CLOSING_SOON);
        assertThat(DeadlineStatus.of(DEADLINE, DEADLINE, WINDOW)).isEqualTo(DeadlineStatus.CLOSED);   // at the deadline = closed
    }
}
