package com.eveassist.api.sde.chr.entity;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ChrFaction implements Serializable {
    @Serial
    private static final long serialVersionUID = -3862478133895991321L;
    private Integer id;
    private String factionName;
    private String description;
    private Integer raceIDs;
    private Integer solarSystemID;
    private Integer corporationID;
    private Double sizeFactor;
    private Integer stationCount;
    private Integer stationSystemCount;
    private Integer militiaCorporationID;
    private Integer iconID;
}