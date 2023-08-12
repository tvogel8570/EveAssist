package com.eveassist.api.esi.pi.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record PlanetPiDto(
        Integer systemId,
        String systemName,
        Integer planetId,
        String planetName,
        String planetType,
        String piClassification,
        List<PiFactory> factories,
        List<PiProduct> products,
        List<PiExtractor> extractors,
        List<PiStorage> storage,
        LocalDateTime lastUpdate
) {
}
