package com.eveassist.api.esi.response;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PlanetExtractorDto(
        Integer cycle_time,
        Double head_radius,
        PlanetExtractorHeadDto[] heads,
        Integer product_type_id,
        Integer qty_per_cycle
) {
}
