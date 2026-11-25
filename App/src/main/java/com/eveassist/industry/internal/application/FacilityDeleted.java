package com.eveassist.industry.internal.application;

/** What deleting a facility did to the viewer's projects: how many Drafts used it, and so now have no facility there and are out of date. */
public record FacilityDeleted(int draftProjectsChanged) {
}
