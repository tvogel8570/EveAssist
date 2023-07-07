package com.eveassist.api.esi.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PilotPublicDto(
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
        String title,
        String px64x64,
        String px128x128,
        String px256x256,
        String px512x512,
        String allianceDesc,
        String corporationDesc,
        String factionDesc,
        String raceDesc,
        String bloodlineDesc
) {
}
