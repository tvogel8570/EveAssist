package com.eveassist.api.esi.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.io.Serializable;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public record UniverseNamesDto(
        String category,
        Integer id,
        String name
) implements Serializable {
}
