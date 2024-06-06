package com.eveassist.client.pilot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PilotMapperTest {

    final PilotMapper cut = new PilotMapperImpl();

    @Test
    void contextLoads() {
        assertThat(cut).isNotNull();
    }

    @Test
    void givenValidPublicData_whenMapped_thenNoExceptions() {
        String publicData = """
                {
                    "allianceId": 588312332,
                    "birthday": "06-27-2006 12:14",
                    "bloodlineId": 11,
                    "corporationId": 226222276,
                    "description": "",
                    "gender": "female",
                    "name": "Cass Tamuri",
                    "raceId": 1,
                    "securityStatus": 2.319465757,
                    "px64x64": "https://images.evetech.net/characters/641131593/portrait?tenant=tranquility&size=64",
                    "px128x128": "https://images.evetech.net/characters/641131593/portrait?tenant=tranquility&size=128",
                    "px256x256": "https://images.evetech.net/characters/641131593/portrait?tenant=tranquility&size=256",
                    "px512x512": "https://images.evetech.net/characters/641131593/portrait?tenant=tranquility&size=512",
                    "allianceDesc": "Nebula Rasa",
                    "corporationDesc": "Nebula Rasa Holdings",
                    "raceDesc": "Caldari",
                    "bloodlineDesc": "Achura"
                }""";
    }
}