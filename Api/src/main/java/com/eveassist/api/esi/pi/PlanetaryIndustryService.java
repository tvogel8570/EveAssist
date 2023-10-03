package com.eveassist.api.esi.pi;

import com.eveassist.api.esi.pi.dto.PlanetPiDto;
import com.eveassist.api.esi.response.CharactersPlanetsDetailDto;
import com.eveassist.api.esi.response.CharactersPlanetsDto;

public interface PlanetaryIndustryService {
    PlanetPiDto summarizePlanet(CharactersPlanetsDto planetsDto, CharactersPlanetsDetailDto planetsDetailDto);
}
