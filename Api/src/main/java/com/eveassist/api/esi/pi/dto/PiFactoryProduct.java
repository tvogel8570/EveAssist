package com.eveassist.api.esi.pi.dto;

import lombok.Builder;

@Builder
public record PiFactoryProduct(
        Integer typeId,
        String name,
        Long quantity
) {
}
