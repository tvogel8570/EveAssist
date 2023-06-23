package com.eveassist.client.pilot.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record PilotDto(
        Long pilotId,
        String pilotName,
        List<String> scope
) {
}
