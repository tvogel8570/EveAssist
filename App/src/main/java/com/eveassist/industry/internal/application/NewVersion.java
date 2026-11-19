package com.eveassist.industry.internal.application;

import com.eveassist.industry.FacilityId;
import java.util.List;

/**
 * A project made a Draft again from a quote. A quote keeps the values of the facilities it was costed with, and a facility can change or
 * be deleted afterwards, so these say where the Draft is not what the quote was costed with: a facility whose values differ now (still
 * used, with its values as they are now) and one that is gone (its items and the project's default fall back to none, and costing says so).
 */
public record NewVersion(ProjectChanged project, List<FacilityId> changedFacilities, List<FacilityId> goneFacilities) {

    public NewVersion {
        changedFacilities = List.copyOf(changedFacilities);
        goneFacilities = List.copyOf(goneFacilities);
    }
}
