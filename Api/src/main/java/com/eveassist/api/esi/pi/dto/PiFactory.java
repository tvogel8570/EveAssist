package com.eveassist.api.esi.pi.dto;

public record PiFactory(
        Integer schematicId,
        String schematicName,
        Integer cycleTime,
        Integer pinTypeId,
        String factoryType,
        Integer inputTypeId,
        String inputName,
        Integer inputQty,
        Integer outputTypeId,
        String outputName,
        Integer outputQty
) {

}
