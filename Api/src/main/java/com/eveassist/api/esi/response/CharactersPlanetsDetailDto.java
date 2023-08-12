package com.eveassist.api.esi.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Arrays;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CharactersPlanetsDetailDto(
        PlanetLinkDto[] links,
        PlanetPinDto[] pins,
        PlanetRoutesDto[] routes
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CharactersPlanetsDetailDto that = (CharactersPlanetsDetailDto) o;

        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(links, that.links)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(pins, that.pins)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(routes, that.routes);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(links);
        result = 31 * result + Arrays.hashCode(pins);
        result = 31 * result + Arrays.hashCode(routes);
        return result;
    }
}
