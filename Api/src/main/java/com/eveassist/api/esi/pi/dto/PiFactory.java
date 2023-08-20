package com.eveassist.api.esi.pi.dto;

import java.util.List;

public record PiFactory(
        Integer schematicId,
        String schematicName,
        Integer cycleTime,
        Integer pinTypeId,
        String factoryType,
        List<PiFactoryProduct> inputs,
        PiFactoryProduct output) {
}
