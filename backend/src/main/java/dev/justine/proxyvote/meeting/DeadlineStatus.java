package dev.justine.proxyvote.meeting;

import java.time.Duration;
import java.time.Instant;

public enum DeadlineStatus {
    OPEN, CLOSING_SOON, CLOSED;

    /** Pure function of (deadline, now): trivially testable, and "now" comes from an injected Clock. */
    public static DeadlineStatus of(Instant deadline, Instant now, Duration closingSoonWindow) {
        if (!now.isBefore(deadline)) return CLOSED;
        if (Duration.between(now, deadline).compareTo(closingSoonWindow) <= 0) return CLOSING_SOON;
        return OPEN;
    }
}
