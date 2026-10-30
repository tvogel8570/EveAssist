package com.eveassist.spike.industry;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Also thrown for a plan the viewer may not see, so that its existence is not revealed. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PlanNotFoundException extends RuntimeException {
    public PlanNotFoundException(PlanId id) {
        super("No plan " + id.value());
    }
}
