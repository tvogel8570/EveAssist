package com.eveassist.api.sde.pi;

import com.eveassist.api.sde.pi.entity.PiViewDto;

public interface PiDao {
    PiViewDto piDetailsBySchematicId(Integer schematicId);

    PiViewDto piDetailsBySchematicIdAndFactoryId(Integer schematicId, Integer factoryId);
}
