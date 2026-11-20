package com.eveassist.industry;

import java.util.Objects;
import java.util.UUID;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record ProjectId(UUID value) {

    public ProjectId {
        Objects.requireNonNull(value);
    }
}
