package com.eveassist.api.esi.response;

import java.io.Serializable;

public record CharactersAffiliationDto(
        Integer alliance_id,
        Integer character_id,
        Integer corporation_id,
        Integer faction_id
) implements Serializable {
}
