package com.eveassist.industry.internal.application;

import com.eveassist.industry.FacilityId;
import com.eveassist.industry.internal.domain.FacilityTerms;
import java.time.Instant;

/** A facility of the viewer's as a page lists it, with what it does to a job and when a value last changed. */
public record FacilityOverview(FacilityId id, String name, FacilityTerms terms, Instant updatedAt) {
}
