package com.eveassist.api.esi.response;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

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
