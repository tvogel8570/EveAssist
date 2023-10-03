package com.eveassist.client.pilot.impl;

import com.eveassist.client.exception.EsiParsingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PilotAuthPayloadTest {
    final ObjectMapper mapper = new ObjectMapper();
    final String esiPayload = """
            {"scp":["esi-characters.read_notifications.v1",
                "esi-killmails.read_killmails.v1",
                "esi-skills.read_skills.v1",
                "esi-skills.read_skillqueue.v1"],
            "jti":"6ba33fa1-3140-4ec8-93f9-b2e2eca9b399",
            "kid":"JWT-Signature-Key",
            "sub":"CHARACTER:EVE:458639815",
            "azp":"bddc8549a6374ae0927f399abd0a7955",
            "tenant":"tranquility",
            "tier":"live",
            "region":"world",
            "aud":["bddc8549a6374ae0927f399abd0a7955",
                "EVE Online"],
            "name":"Erich Benford",
            "owner":"02pOVYfpTACooKRp99RjQt1/lfQ=",
            "exp":1696253493,
            "iat":1696252293,
            "iss":"login.eveonline.com"}""";

    @Test
    void givenValidEsiResponse_thenParsePilot() {
        try {
            PilotAuthPayload pilotAuthPayload = mapper.readValue(esiPayload, PilotAuthPayload.class);
            assertThat(pilotAuthPayload.name()).isNotNull().isEqualTo("Erich Benford");
            assertThat(pilotAuthPayload.scp()).isNotNull().hasSize(4);
        } catch (JsonProcessingException e) {
            throw new EsiParsingException(e, "savePilot", "PilotAuthPayload");
        }
    }
}
