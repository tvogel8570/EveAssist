package com.eveassist.api.esi.pi.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PiExtractor(
        LocalDateTime expiryTime,
        LocalDateTime installTime,
        LocalDateTime lastCycleStart,
        Integer cycleTime,
        Integer numHeads,
        Integer productTypeId,
        String productName,
        Integer qtyPerCycle,
        Double headRadius
) {
}
