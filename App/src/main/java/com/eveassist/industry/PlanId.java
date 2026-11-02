package com.eveassist.industry;

import java.util.Objects;
import java.util.UUID;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record PlanId(UUID value) {

    public PlanId {
        Objects.requireNonNull(value);
    }
}
