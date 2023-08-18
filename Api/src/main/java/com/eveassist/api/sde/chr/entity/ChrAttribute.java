package com.eveassist.api.sde.chr.entity;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ChrAttribute implements Serializable {
    @Serial
    private static final long serialVersionUID = -4714571540812408300L;
    private Integer id;
    private String attributeName;
    private String description;
    private Integer iconID;
    private String shortDescription;
    private String notes;
}