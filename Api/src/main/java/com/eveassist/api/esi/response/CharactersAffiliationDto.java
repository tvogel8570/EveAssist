package com.eveassist.api.esi.response;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

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
