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
    // @formatter:off
    @Mapping(target = "allianceId",
            expression = "java(publicData.allianceId()!=null?publicData.allianceId():affiliationData!=null?affiliationData.allianceId():null)")
    @Mapping(target = "corporationId",
            expression = "java(publicData.corporationId()!=null?publicData.corporationId():affiliationData!=null?affiliationData.corporationId():null)")
    @Mapping(target = "factionId",
            expression = "java(publicData.factionId()!=null?publicData.factionId():affiliationData!=null?affiliationData.factionId():null)")
    @Mapping(source = "descDto.allianceDesc", target = "allianceDesc")
    @Mapping(source = "descDto.corporationDesc", target = "corporationDesc")
    @Mapping(source = "descDto.factionDesc", target = "factionDesc")
    // @formatter:on
    PilotPublicDto from(CharactersDto publicData, CharactersPortraitDto portraitData,
                        CharactersAffiliationDto affiliationData, AffiliationDescDto descDto);
}
