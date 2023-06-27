package com.eveassist.api.esi.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AffiliationDescDto {
    private String allianceDesc;
    private String corporationDesc;
    private String factionDesc;
    private String characterDesc;
}
