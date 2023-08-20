package com.eveassist.api.sde.pi.entity;

import lombok.*;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PiPinDto {
    private Integer typeId;
    private String typeName;
    private Integer capacity;
    private Integer graphicId;
    private String groupName;
}
