package com.eveassist.api.esi.pi.dto;

import lombok.Builder;

@Builder
public record PiProduct(
        Boolean isInput,
        Integer productTypeId,
        String productName,
        Long amount
) {
}
