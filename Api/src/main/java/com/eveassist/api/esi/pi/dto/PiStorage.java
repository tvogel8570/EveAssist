package com.eveassist.api.esi.pi.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record PiStorage(
        String name,
        Integer capacity,
        Boolean isLaunchpad,
        LocalDateTime lastCycleStart,
        List<PiProduct> products
) {
}
