package com.eveassist.api.esi.pi.dto;

import lombok.Builder;

@Builder
public record PiProduct(
        Integer productTypeId,
        String productName,
        Long amount
) {
}
