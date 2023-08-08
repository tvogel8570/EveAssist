package com.eveassist.api.esi.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PlanetPinDto(
        PlanetPinContentsDto[] contents,
        LocalDateTime last_cycle_start,
        Double latitude,
        Double longitude,
        Long pin_id,
        Integer schematic_id,
        Integer type_id,
        // optional Extractor planet
        LocalDateTime expiry_time,
        PlanetExtractorDto extractor_details,
        LocalDateTime install_time
) {
}
