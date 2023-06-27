package com.eveassist.api.esi.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.io.Serializable;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public record CharactersPortraitDto(
        String px64x64,
        String px128x128,
        String px256x256,
        String px512x512
) implements Serializable {
}
