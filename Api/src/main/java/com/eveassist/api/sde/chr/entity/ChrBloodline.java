package com.eveassist.api.sde.chr.entity;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ChrBloodline implements Serializable {
    @Serial
    private static final long serialVersionUID = 1712235104334212689L;
    private Integer id;
    private String bloodlineName;
    private Integer raceID;
    private String description;
    private String maleDescription;
    private String femaleDescription;
    private Integer shipTypeID;
    private Integer corporationID;
    private Integer perception;
    private Integer willpower;
    private Integer charisma;
    private Integer memory;
    private Integer intelligence;
    private Integer iconID;
    private String shortDescription;
    private String shortMaleDescription;
    private String shortFemaleDescription;
}