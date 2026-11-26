package com.eveassist.industry.internal.facility.application;

import com.eveassist.industry.FacilityId;
import com.eveassist.industry.internal.domain.FacilityTerms;
import com.eveassist.industry.internal.facility.domain.StructureType;
import java.time.Instant;

/**
 * A facility of the viewer's as a page lists it: what it does to a job, which structure it is (none when not said), when any value last
 * changed, and when the cost index was last typed (the index is typed, so its age is worth showing).
 */
public record FacilityOverview(
        FacilityId id, String name, StructureType structureType, FacilityTerms terms, Instant updatedAt, Instant indexEnteredAt) {
}
