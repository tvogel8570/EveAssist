package com.eveassist.api.sde.chr.dto;

import java.io.Serializable;

/**
 * DTO for {@link com.eveassist.api.sde.chr.entity.ChrAttribute}
 */
public record ChrAttributeDto(Integer id, String attributeName, String description, Integer iconID,
                              String shortDescription, String notes) implements Serializable {
    private static final long serialVersionUID = -4002099937462440269L;
}