package com.eveassist.api.esi.response;

import java.io.Serializable;

public record CharactersPortraitDto(
        String px64x64,
        String px128x128,
        String px256x256,
        String px512x512
) implements Serializable {
}
