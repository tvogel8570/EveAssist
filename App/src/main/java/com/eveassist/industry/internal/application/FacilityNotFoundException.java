package com.eveassist.industry.internal.application;

/** A project refers to a facility that does not exist or belongs to someone else. The two look the same. */
public class FacilityNotFoundException extends RuntimeException {

    public FacilityNotFoundException() {
        super("No such facility");
    }
}
