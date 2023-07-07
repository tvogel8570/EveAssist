package com.eveassist.api.sde.chr.dto;

import java.io.Serializable;

/**
 * DTO for {@link com.eveassist.api.sde.chr.entity.ChrBloodline}
 */
public record ChrBloodlineDto(Integer id, String bloodlineName, Integer raceID, String description,
                              String maleDescription, String femaleDescription, Integer shipTypeID,
                              Integer corporationID, Integer perception, Integer willpower, Integer charisma,
                              Integer memory, Integer intelligence, Integer iconID, String shortDescription,
                              String shortMaleDescription, String shortFemaleDescription) implements Serializable {
    private static final long serialVersionUID = 9060779319409113481L;
}