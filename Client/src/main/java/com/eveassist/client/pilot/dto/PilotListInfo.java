package com.eveassist.client.pilot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Projection for {@link com.eveassist.client.pilot.entity.Pilot}
 * For displaying a list of pilots on the UI
 */

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PilotListInfo {
    private String ownerHash;
    private Long evePilotId;
    private String name;
    private String px64x64;
    private String allianceDesc;
    private String corporationDesc;
    private String factionDesc;

    @SuppressWarnings("unused")
    public static final class PilotListInfoBuilder {
        private String ownerHash;
        private Long evePilotId;
        private String name;
        private String px64x64;
        private String allianceDesc;
        private String corporationDesc;
        private String factionDesc;

        private PilotListInfoBuilder() {
        }

        public static PilotListInfoBuilder aPilotListInfo() {
            return new PilotListInfoBuilder();
        }

        public PilotListInfoBuilder withOwnerHash(String ownerHash) {
            this.ownerHash = ownerHash;
            return this;
        }

        public PilotListInfoBuilder withEvePilotId(Long evePilotId) {
            this.evePilotId = evePilotId;
            return this;
        }

        public PilotListInfoBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public PilotListInfoBuilder withPx64x64(String px64x64) {
            this.px64x64 = px64x64;
            return this;
        }

        public PilotListInfoBuilder withAllianceDesc(String allianceDesc) {
            this.allianceDesc = allianceDesc;
            return this;
        }

        public PilotListInfoBuilder withCorporationDesc(String corporationDesc) {
            this.corporationDesc = corporationDesc;
            return this;
        }

        public PilotListInfoBuilder withFactionDesc(String factionDesc) {
            this.factionDesc = factionDesc;
            return this;
        }

        public PilotListInfo build() {
            PilotListInfo pilotListInfo = new PilotListInfo();
            pilotListInfo.setOwnerHash(ownerHash);
            pilotListInfo.setEvePilotId(evePilotId);
            pilotListInfo.setName(name);
            pilotListInfo.setPx64x64(px64x64);
            pilotListInfo.setAllianceDesc(allianceDesc);
            pilotListInfo.setCorporationDesc(corporationDesc);
            pilotListInfo.setFactionDesc(factionDesc);
            return pilotListInfo;
        }
    }
}