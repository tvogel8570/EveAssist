package com.eveassist.api.esi.pi.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Set;

@Builder
public record PilotPiDto(
        Integer pilotId,
        String pilotName,
        LocalDateTime asOf,
        Set<PlanetPiDto> planets
) {
}
