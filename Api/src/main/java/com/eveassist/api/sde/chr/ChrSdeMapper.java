package com.eveassist.api.sde.chr;

import com.eveassist.api.sde.chr.dto.*;
import com.eveassist.api.sde.chr.entity.*;
import org.mapstruct.*;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface ChrSdeMapper {
    ChrAttribute toEntity(ChrAttributeDto chrAttributeDto);

    ChrAttributeDto toDto(ChrAttribute chrAttribute);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    ChrAttribute partialUpdate(ChrAttributeDto chrAttributeDto, @MappingTarget ChrAttribute chrAttribute);

    ChrAncestry toEntity(ChrAncestryDto chrAncestryDto);

    ChrAncestryDto toDto(ChrAncestry chrAncestry);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    ChrAncestry partialUpdate(ChrAncestryDto chrAncestryDto, @MappingTarget ChrAncestry chrAncestry);

    ChrBloodline toEntity(ChrBloodlineDto chrBloodlineDto);

    ChrBloodlineDto toDto(ChrBloodline chrBloodline);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    ChrBloodline partialUpdate(ChrBloodlineDto chrBloodlineDto, @MappingTarget ChrBloodline chrBloodline);

    ChrFaction toEntity(ChrFactionDto chrFactionDto);

    ChrFactionDto toDto(ChrFaction chrFaction);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    ChrFaction partialUpdate(ChrFactionDto chrFactionDto, @MappingTarget ChrFaction chrFaction);

    ChrRace toEntity(ChrRaceDto chrRaceDto);

    ChrRaceDto toDto(ChrRace chrRace);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    ChrRace partialUpdate(ChrRaceDto chrRaceDto, @MappingTarget ChrRace chrRace);
}