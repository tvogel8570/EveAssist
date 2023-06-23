package com.eveassist.client.pilot.impl;

import com.eveassist.client.pilot.PilotService;
import com.eveassist.client.pilot.dto.PilotAuthDto;
import com.eveassist.client.pilot.dto.PilotDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
public class PilotServiceImpl implements PilotService {
    private final ObjectMapper mapper;

    public PilotServiceImpl(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PilotDto savePilot(PilotAuthDto pilotAuth) {
        String[] chunks = pilotAuth.accessToken().split("\\.");

        // TODO verify signature

        Base64.Decoder decoder = Base64.getUrlDecoder();
        String payload = new String(decoder.decode(chunks[1]));

        try {
            PilotAuthPayload pilotAuthPayload = mapper.readValue(payload, PilotAuthPayload.class);
            String[] subject = pilotAuthPayload.sub.split("\\:");
            return PilotDto.builder().pilotName(pilotAuthPayload.name).pilotId(Long.parseLong(subject[2])).build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    static record PilotAuthPayload(
            String jti,
            String kid,
            String sub,
            String azp,
            String tenant,
            String tier,
            String region,
            String aud,
            String name,
            String owner,
            Long exp,
            Long iat,
            String iss
    ) {
    }
}
