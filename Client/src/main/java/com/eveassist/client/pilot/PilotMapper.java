package com.eveassist.client.pilot;

import com.eveassist.client.pilot.dto.PilotDto;
import com.eveassist.client.pilot.dto.PilotListInfo;
import com.eveassist.client.pilot.dto.PilotPublicDto;
import com.eveassist.client.pilot.entity.Pilot;
import org.mapstruct.*;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface PilotMapper {
    Pilot toEntity(PilotDto pilotDto);

    PilotDto toDto(Pilot pilot);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Pilot partialUpdate(PilotDto pilotDto, @MappingTarget Pilot pilot);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    PilotListInfo partialUpdateDetails(PilotPublicDto publicData, @MappingTarget PilotListInfo info);
}