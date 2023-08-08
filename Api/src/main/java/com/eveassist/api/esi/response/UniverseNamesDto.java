package com.eveassist.api.esi.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.io.Serializable;

/*
    CCP defined response object for /universe/names/
 */

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UniverseNamesDto(
        String category,
        Integer id,
        String name
) implements Serializable {
}
