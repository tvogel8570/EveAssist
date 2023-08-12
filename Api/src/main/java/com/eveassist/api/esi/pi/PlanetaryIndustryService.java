package com.eveassist.api.esi.pi;

import com.eveassist.api.esi.pi.dto.PlanetPiDto;
import com.eveassist.api.esi.response.CharactersPlanetsDetailDto;

public interface PlanetaryIndustryService {
    PlanetPiDto summarizePlanet(CharactersPlanetsDetailDto planetsDetailDto);
}
