package com.eveassist.api.esi.response;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PlanetLinkDto(
        Long destination_pin_id,
        Integer link_level,
        Long source_pin_id) {
}
