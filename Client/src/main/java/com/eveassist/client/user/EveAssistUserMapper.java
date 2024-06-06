package com.eveassist.client.user;

import com.eveassist.client.user.dto.EveAssistUserDto;
import com.eveassist.client.user.dto.EveAssistUserListDto;
import com.eveassist.client.user.entity.EveAssistUser;
import org.mapstruct.*;

@Mapper(unmappedTargetPolicy = ReportingPolicy.WARN, componentModel = MappingConstants.ComponentModel.SPRING)
public interface EveAssistUserMapper {
    @Mapping(target = "updateTimestamp", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createTimestamp", ignore = true)
    EveAssistUser toEntity(EveAssistUserDto eveAssistUserDto);

    EveAssistUserDto toDto(EveAssistUser eveAssistUser);

    @Mapping(target = "updateTimestamp", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createTimestamp", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    EveAssistUser partialUpdate(EveAssistUserDto eveAssistUserDto, @MappingTarget EveAssistUser eveAssistUser);

    EveAssistUserListDto toList(EveAssistUser eveAssistUser);
}