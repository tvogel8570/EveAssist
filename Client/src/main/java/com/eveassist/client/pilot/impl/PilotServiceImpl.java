package com.eveassist.client.pilot.impl;

import com.eveassist.client.pilot.PilotService;
import com.eveassist.client.pilot.dto.PilotAuthDto;
import com.eveassist.client.pilot.dto.PilotDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class PilotServiceImpl implements PilotService {
    private final ObjectMapper mapper;
    private final List<PilotDto> pilots = new ArrayList<>();

    public PilotServiceImpl(ObjectMapper mapper) {
        this.mapper = mapper;
        pilots.add(PilotDto.builder().pilotId(1L).pilotName("Cass").build());
        pilots.add(PilotDto.builder().pilotId(2L).pilotName("Michi").build());
        pilots.add(PilotDto.builder().pilotId(3L).pilotName("Dnai").build());
    }

    @Override
    public PilotDto savePilot(PilotAuthDto pilotAuth) {
        String[] chunks = pilotAuth.accessToken().split("\\.");

        // TODO verify signature

        Base64.Decoder decoder = Base64.getUrlDecoder();
        String payload = new String(decoder.decode(chunks[1]));

        try {
            PilotAuthPayload pilotAuthPayload = mapper.readValue(payload, PilotAuthPayload.class);
            String[] subject = pilotAuthPayload.sub.split(":");
            PilotDto newPilot = PilotDto.builder().pilotName(pilotAuthPayload.name).pilotId(Long.parseLong(subject[2])).build();
            this.pilots.add(newPilot);
            return newPilot;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<PilotDto> getAllPilots() {
        return pilots;
    }

    @Override
    public void linkState(String name, String state) {
        log.info("name: [%s] state: [%s]".formatted(name, state));
    }

    @Override
    public Optional<String> getUserFromState(String state) {
        return Optional.empty();
    }

    record PilotAuthPayload(
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
