package com.eveassist.api.sde.pi;

import com.eveassist.api.sde.pi.entity.PiPinDto;
import com.eveassist.api.sde.pi.entity.PiViewDto;

import java.util.List;

public interface PiDao {
    List<PiViewDto> piDetailsBySchematicId(Integer schematicId);

    List<PiViewDto> piDetailsBySchematicIdAndFactoryId(Integer schematicId, Integer factoryId);

    PiPinDto piPinByType(Integer pinType);

    String typeNameByTypeId(Integer typeId);

    String celestialNameById(Integer id);
}
