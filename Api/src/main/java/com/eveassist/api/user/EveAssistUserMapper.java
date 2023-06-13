package com.eveassist.api.user;

import com.eveassist.api.user.dto.EveAssistUserDto;
import com.eveassist.api.user.dto.EveAssistUserListDto;
import com.eveassist.api.user.entity.EveAssistUser;
import org.mapstruct.*;

@Mapper(unmappedTargetPolicy = ReportingPolicy.WARN, componentModel = MappingConstants.ComponentModel.SPRING)
public interface EveAssistUserMapper {
    @Mapping(target = "updateTimestamp", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "credentialsNonExpired", ignore = true)
    @Mapping(target = "createTimestamp", ignore = true)
    @Mapping(target = "accountNonLocked", ignore = true)
    @Mapping(target = "accountNonExpired", ignore = true)
    EveAssistUser toEntityFromDto(EveAssistUserDto eveAssistUserDto);

    @Mapping(target = "loginOk",
            expression = """
                    java(eveAssistUser.isAccountNonExpired()
                    	&& eveAssistUser.isAccountNonLocked()
                    	&& eveAssistUser.isCredentialsNonExpired()
                    	&& eveAssistUser.isEnabled())""")
    EveAssistUserDto toUserDto(EveAssistUser eveAssistUser);

    @Mapping(target = "updateTimestamp", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "credentialsNonExpired", ignore = true)
    @Mapping(target = "createTimestamp", ignore = true)
    @Mapping(target = "accountNonLocked", ignore = true)
    @Mapping(target = "accountNonExpired", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    EveAssistUser partialUpdateFromDto(
            EveAssistUserDto eveAssistUserDto, @MappingTarget EveAssistUser eveAssistUser);

    EveAssistUserListDto toUserList(EveAssistUser eveAssistUser);
}