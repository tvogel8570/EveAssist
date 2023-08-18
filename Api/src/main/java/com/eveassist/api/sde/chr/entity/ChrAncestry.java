package com.eveassist.api.sde.chr.entity;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ChrAncestry implements Serializable {
    @Serial
    private static final long serialVersionUID = -6547811978630402275L;
    private Integer ancestryID;
    private String ancestryName;
    private Integer bloodlineID;
    private String description;
    private Integer perception;
    private Integer willpower;
    private Integer charisma;
    private Integer memory;
    private Integer intelligence;
    private Integer iconID;
    private String shortDescription;
}