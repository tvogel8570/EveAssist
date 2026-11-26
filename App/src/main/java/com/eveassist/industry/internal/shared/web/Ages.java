package com.eveassist.industry.internal.shared.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** How old a time is, in whole days, for a page that shows how long ago a value was typed. */
@Component("ages")
class Ages {

    private final Clock clock;

    Ages(Clock clock) {
        this.clock = clock;
    }

    /** Whole days from the time until now, never below 0. */
    public long days(Instant time) {
        return Math.max(0, Duration.between(time, clock.instant()).toDays());
    }
}
