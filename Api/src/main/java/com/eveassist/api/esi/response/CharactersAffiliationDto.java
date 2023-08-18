package com.eveassist.api.esi.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.io.Serializable;

/*
    CCP defined response object for /characters/affiliation/
 */

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CharactersAffiliationDto(
        Integer allianceId,
        Integer characterId,
        Integer corporationId,
        Integer factionId
) implements Serializable {
}
