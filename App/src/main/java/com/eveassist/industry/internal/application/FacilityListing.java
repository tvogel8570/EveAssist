package com.eveassist.industry.internal.application;

/** A facility with the number of the viewer's Draft projects that use it (as the default or for an item), for a page that offers to delete it. */
public record FacilityListing(FacilityOverview facility, int draftProjects) {
}
