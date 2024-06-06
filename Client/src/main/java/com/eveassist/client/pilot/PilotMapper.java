package com.eveassist.client.pilot;

import com.eveassist.client.pilot.dto.PilotDto;
import com.eveassist.client.pilot.dto.PilotListInfo;
import com.eveassist.client.pilot.dto.PilotPublicDto;
import com.eveassist.client.pilot.entity.Pilot;
import org.mapstruct.*;

@Mapper(unmappedTargetPolicy = ReportingPolicy.WARN, componentModel = MappingConstants.ComponentModel.SPRING)
public interface PilotMapper {
    Pilot toEntity(PilotDto pilotDto);

    PilotDto toDto(Pilot pilot);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Pilot partialUpdate(PilotDto pilotDto, @MappingTarget Pilot pilot);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    PilotListInfo partialUpdateDetails(PilotPublicDto publicData, @MappingTarget PilotListInfo info);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(source = "px64x64", target = "portraitUrlTiny")
    @Mapping(source = "px128x128", target = "portraitUrlSmall")
    @Mapping(source = "px256x256", target = "portraitUrlMedium")
    @Mapping(source = "px512x512", target = "portraitUrlLarge")
    void partialUpdateDetails(PilotPublicDto publicData, @MappingTarget Pilot pilot);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    PilotListInfo toListDto(Pilot newPilot);
}