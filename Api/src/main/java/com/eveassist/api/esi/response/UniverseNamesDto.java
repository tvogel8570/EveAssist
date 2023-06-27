package com.eveassist.api.esi.response;

import java.io.Serializable;

public record UniverseNamesDto(
        String category,
        Integer id,
        String name
) implements Serializable {
}
