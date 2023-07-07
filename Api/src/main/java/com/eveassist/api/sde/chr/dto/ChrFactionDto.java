package com.eveassist.api.sde.chr.dto;

import java.io.Serializable;

/**
 * DTO for {@link com.eveassist.api.sde.chr.entity.ChrFaction}
 */
public record ChrFactionDto(Integer id, String factionName, String description, Integer raceIDs, Integer solarSystemID,
                            Integer corporationID, Double sizeFactor, Integer stationCount, Integer stationSystemCount,
                            Integer militiaCorporationID, Integer iconID) implements Serializable {
    private static final long serialVersionUID = 6399283140005292406L;
}