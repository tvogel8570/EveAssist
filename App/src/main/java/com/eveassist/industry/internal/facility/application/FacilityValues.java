package com.eveassist.industry.internal.facility.application;

import com.eveassist.industry.internal.costing.domain.FacilityTerms;
import com.eveassist.industry.internal.facility.domain.StructureType;

/**
 * What a facility is made of when it is added or changed: a name, which structure it is (or null), and its terms. A name is stripped, and a
 * blank or over-long one is refused, whatever the page allowed. The terms refuse a percent outside 0 to 100 themselves.
 */
public record FacilityValues(String name, StructureType structureType, FacilityTerms terms) {

    public static final int MAX_NAME_LENGTH = 100;

    public FacilityValues {
        if (terms == null) {
            throw new IllegalArgumentException("A facility needs its terms");
        }
        name = name == null ? "" : name.strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new InvalidFacilityNameException();
        }
    }
}
