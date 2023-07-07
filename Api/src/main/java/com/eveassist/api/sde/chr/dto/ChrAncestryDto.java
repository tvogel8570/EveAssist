package com.eveassist.api.sde.chr.dto;

import java.io.Serializable;

/**
 * DTO for {@link com.eveassist.api.sde.chr.entity.ChrAncestry}
 */
public record ChrAncestryDto(Integer id, String ancestryName, Integer bloodlineID, String description,
                             Integer perception, Integer willpower, Integer charisma, Integer memory,
                             Integer intelligence, Integer iconID, String shortDescription) implements Serializable {
    private static final long serialVersionUID = -7837114277257433991L;
}