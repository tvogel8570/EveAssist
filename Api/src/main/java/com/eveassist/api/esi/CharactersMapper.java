package com.eveassist.api.esi;

import com.eveassist.api.esi.dto.PilotPublicDto;
import com.eveassist.api.esi.response.AffiliationDescDto;
import com.eveassist.api.esi.response.CharactersAffiliationDto;
import com.eveassist.api.esi.response.CharactersDto;
import com.eveassist.api.esi.response.CharactersPortraitDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CharactersMapper {

    @Mapping(source = "affiliationData.alliance_id", target = "alliance_id")
    @Mapping(source = "affiliationData.corporation_id", target = "corporation_id")
    @Mapping(source = "affiliationData.faction_id", target = "faction_id")
    @Mapping(source = "descDto.allianceDesc", target = "alliance_desc")
    @Mapping(source = "descDto.corporationDesc", target = "corporation_desc")
    @Mapping(source = "descDto.factionDesc", target = "faction_desc")
    PilotPublicDto from(CharactersDto publicData, CharactersPortraitDto portraitData,
                        CharactersAffiliationDto affiliationData, AffiliationDescDto descDto);
}
