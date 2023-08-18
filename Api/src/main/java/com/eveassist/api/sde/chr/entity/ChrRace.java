package com.eveassist.api.sde.chr.entity;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ChrRace implements Serializable {
    @Serial
    private static final long serialVersionUID = -4793478671011562663L;
    private Integer id;
    private String raceName;
    private String description;
    private Integer iconID;
    private String shortDescription;
}