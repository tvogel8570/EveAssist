package com.eveassist.api.sde.chr.dto;

import java.io.Serializable;

/**
 * DTO for {@link com.eveassist.api.sde.chr.entity.ChrRace}
 */
public record ChrRaceDto(Integer id, String raceName, String description, Integer iconID,
                         String shortDescription) implements Serializable {
    private static final long serialVersionUID = -1659876159515775695L;
}