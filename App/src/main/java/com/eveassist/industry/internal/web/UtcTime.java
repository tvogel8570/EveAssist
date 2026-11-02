package com.eveassist.industry.internal.web;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/** Formats times for templates. Each user's own time zone comes later. */
@Component("utcTime")
class UtcTime {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    public String format(Instant time) {
        return FORMAT.format(time);
    }
}
