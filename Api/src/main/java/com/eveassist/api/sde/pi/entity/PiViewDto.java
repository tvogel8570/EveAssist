package com.eveassist.api.sde.pi.entity;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PiViewDto implements Serializable {
    @Serial
    private static final long serialVersionUID = -5283470718465885865L;
    private Integer schematicId;
    private String schematicName;
    private Integer cycleTime;
    private Integer factoryTypeId;
    private String factoryName;
    private Integer productTypeId;
    private String productName;
    private Boolean isInput;
    private Integer quantity;
    private String factoryType;
}
