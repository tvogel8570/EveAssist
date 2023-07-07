package com.eveassist.api.esi.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AffiliationDescDto {
    private String allianceDesc;
    private String corporationDesc;
    private String factionDesc;
    private String characterDesc;
    private String raceDesc;
    private String bloodlineDesc;

}
