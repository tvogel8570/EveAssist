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
    @Mapping(target = "alliance_id", expression = "java(publicData.alliance_id()!=null?publicData.alliance_id():affiliationData!=null?affiliationData.alliance_id():null)")
    @Mapping(target = "corporation_id", expression = "java(publicData.corporation_id()!=null?publicData.corporation_id():affiliationData!=null?affiliationData.corporation_id():null)")
    @Mapping(target = "faction_id", expression = "java(publicData.faction_id()!=null?publicData.faction_id():affiliationData!=null?affiliationData.faction_id():null)")
    @Mapping(source = "descDto.allianceDesc", target = "alliance_desc")
    @Mapping(source = "descDto.corporationDesc", target = "corporation_desc")
    @Mapping(source = "descDto.factionDesc", target = "faction_desc")
    PilotPublicDto from(CharactersDto publicData, CharactersPortraitDto portraitData,
                        CharactersAffiliationDto affiliationData, AffiliationDescDto descDto);
}
