package com.eveassist.industry.internal.application;

import java.time.Instant;

/** What a project page shows that the aggregate does not hold: when it was started and the name of the plan it started from, if that plan still exists. */
public record ProjectMeta(Instant createdAt, String sourcePlanName) {
}
