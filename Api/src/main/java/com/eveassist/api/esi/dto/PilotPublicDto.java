package com.eveassist.api.esi.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PilotPublicDto(
        Integer alliance_id,
        LocalDateTime birthday,
        Integer bloodline_id,
        Integer corporation_id,
        String description,
        Integer faction_id,
        String gender,
        String name,
        Integer race_id,
        Double security_status,
        String title,
        String px64x64,
        String px128x128,
        String px256x256,
        String px512x512,
        String alliance_desc,
        String corporation_desc,
        String faction_desc
) {
}
