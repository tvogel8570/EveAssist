package com.eveassist.client.pilot.dto;

import lombok.Builder;

@Builder
public record PilotDto(
        String ownerHash,
        Long pilotId,
        String pilotName

//        , List<String> scope
) {
}
