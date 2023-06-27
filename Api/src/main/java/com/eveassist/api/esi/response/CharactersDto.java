package com.eveassist.api.esi.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.io.Serializable;
import java.time.LocalDateTime;


@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public record CharactersDto(
        Integer allianceId,
        LocalDateTime birthday,
        Integer bloodlineId,
        Integer corporationId,
        String description,
        Integer factionId,
        String gender,
        String name,
        Integer raceId,
        Double securityStatus,
        String title) implements Serializable {
}
