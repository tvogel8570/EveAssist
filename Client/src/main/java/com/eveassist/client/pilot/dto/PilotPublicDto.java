package com.eveassist.client.pilot.dto;

public record PilotPublicDto(
        Integer allianceId,
//        Instant birthday,
        Integer bloodlineId,
        Integer corporationId,
        String description,
        Integer factionId,
        String gender,
        String name,
        Integer raceId,
        Double securityStatus,
        String title,
        String px64x64,
        String px128x128,
        String px256x256,
        String px512x512,
        String allianceDesc,
        String corporationDesc,
        String factionDesc
) {
    @SuppressWarnings("unused")
    public static final class PilotPublicDtoBuilder {
        private Integer allianceId;
        private Integer bloodlineId;
        private Integer corporationId;
        private String description;
        private Integer factionId;
        private String gender;
        private String name;
        private Integer raceId;
        private Double securityStatus;
        private String title;
        private String px64x64;
        private String px128x128;
        private String px256x256;
        private String px512x512;
        private String allianceDesc;
        private String corporationDesc;
        private String factionDesc;

        private PilotPublicDtoBuilder() {
        }

        public static PilotPublicDtoBuilder aPilotPublicDto() {
            return new PilotPublicDtoBuilder();
        }

        public PilotPublicDtoBuilder withAllianceId(Integer allianceId) {
            this.allianceId = allianceId;
            return this;
        }

        public PilotPublicDtoBuilder withBloodlineId(Integer bloodlineId) {
            this.bloodlineId = bloodlineId;
            return this;
        }

        public PilotPublicDtoBuilder withCorporationId(Integer corporationId) {
            this.corporationId = corporationId;
            return this;
        }

        public PilotPublicDtoBuilder withDescription(String description) {
            this.description = description;
            return this;
        }

        public PilotPublicDtoBuilder withFactionId(Integer factionId) {
            this.factionId = factionId;
            return this;
        }

        public PilotPublicDtoBuilder withGender(String gender) {
            this.gender = gender;
            return this;
        }

        public PilotPublicDtoBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public PilotPublicDtoBuilder withRaceId(Integer raceId) {
            this.raceId = raceId;
            return this;
        }

        public PilotPublicDtoBuilder withSecurityStatus(Double securityStatus) {
            this.securityStatus = securityStatus;
            return this;
        }

        public PilotPublicDtoBuilder withTitle(String title) {
            this.title = title;
            return this;
        }

        public PilotPublicDtoBuilder withPx64x64(String px64x64) {
            this.px64x64 = px64x64;
            return this;
        }

        public PilotPublicDtoBuilder withPx128x128(String px128x128) {
            this.px128x128 = px128x128;
            return this;
        }

        public PilotPublicDtoBuilder withPx256x256(String px256x256) {
            this.px256x256 = px256x256;
            return this;
        }

        public PilotPublicDtoBuilder withPx512x512(String px512x512) {
            this.px512x512 = px512x512;
            return this;
        }

        public PilotPublicDtoBuilder withAllianceDesc(String allianceDesc) {
            this.allianceDesc = allianceDesc;
            return this;
        }

        public PilotPublicDtoBuilder withCorporationDesc(String corporationDesc) {
            this.corporationDesc = corporationDesc;
            return this;
        }

        public PilotPublicDtoBuilder withFactionDesc(String factionDesc) {
            this.factionDesc = factionDesc;
            return this;
        }

        public PilotPublicDto build() {
            return new PilotPublicDto(allianceId, bloodlineId, corporationId, description, factionId, gender, name, raceId, securityStatus, title, px64x64, px128x128, px256x256, px512x512, allianceDesc, corporationDesc, factionDesc);
        }
    }
}
