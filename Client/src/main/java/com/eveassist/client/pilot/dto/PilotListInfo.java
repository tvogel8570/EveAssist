package com.eveassist.client.pilot.dto;

import lombok.*;

import java.time.LocalDate;

/**
 * Projection for {@link com.eveassist.client.pilot.entity.Pilot}
 */

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PilotListInfo {
    @Setter(AccessLevel.NONE)
    private String ownerHash;
    @Setter(AccessLevel.NONE)
    private Long evePilotId;
    @Setter(AccessLevel.NONE)
    private String name;
    private LocalDate birthday;
    private Integer bloodlineId;
    private String description;
    private String gender;
    private Double securityStatus;
    private String title;
    private String px64x64;
    private String px128x128;
    private String px256x256;
    private String px512x512;
    private String allianceDesc;
    private String corporationDesc;
    private String factionDesc;

    public PilotListInfo(String ownerHash, Long evePilotId, String name, String px64x64) {
        this.ownerHash = ownerHash;
        this.evePilotId = evePilotId;
        this.name = name;
        this.px64x64 = px64x64;
    }
}