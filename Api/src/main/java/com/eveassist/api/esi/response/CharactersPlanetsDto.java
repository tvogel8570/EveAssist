package com.eveassist.api.esi.response;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CharactersPlanetsDto(
        String last_update,
        Integer num_pins,
        Integer owner_id,
        Integer planet_id,
        String planet_type,
        Integer solar_system_id,
        Integer upgrade_level
) {
}
