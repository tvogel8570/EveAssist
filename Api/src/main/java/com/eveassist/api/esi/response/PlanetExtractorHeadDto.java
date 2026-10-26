package com.eveassist.api.esi.response;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PlanetExtractorHeadDto(
        Integer head_id,
        Double latitude,
        Double longitude
) {
}
