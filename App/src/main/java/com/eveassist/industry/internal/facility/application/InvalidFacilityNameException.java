package com.eveassist.industry.internal.facility.application;

/** A facility's name is blank or longer than the limit. */
public class InvalidFacilityNameException extends RuntimeException {

    public InvalidFacilityNameException() {
        super("A facility's name is 1 to " + FacilityValues.MAX_NAME_LENGTH + " characters");
    }
}
