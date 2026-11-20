package com.eveassist.industry;

import java.util.Objects;
import java.util.UUID;
import org.jmolecules.ddd.annotation.ValueObject;

/** A facility's public id. A project refers to a facility by this, never by its row key. */
@ValueObject
public record FacilityId(UUID value) {

    public FacilityId {
        Objects.requireNonNull(value);
    }
}
