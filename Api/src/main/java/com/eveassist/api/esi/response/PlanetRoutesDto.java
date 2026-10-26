package com.eveassist.api.esi.response;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PlanetRoutesDto(
        Integer content_type_id,
        Long destination_pin_id,
        Double quantity,
        Long route_id,
        Long source_pin_id,
        Long[] waypoints
) {
}
