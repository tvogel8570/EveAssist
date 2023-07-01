package com.eveassist.client.pilot.impl;

import com.eveassist.client.exception.EsiParsingException;
import com.eveassist.client.pilot.PilotMapper;
import com.eveassist.client.pilot.PilotRepository;
import com.eveassist.client.pilot.PilotService;
import com.eveassist.client.pilot.dto.PilotAuthDto;
import com.eveassist.client.pilot.dto.PilotDto;
import com.eveassist.client.pilot.dto.PilotListInfo;
import com.eveassist.client.pilot.dto.PilotPublicDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class PilotServiceImpl implements PilotService {
    private final ObjectMapper mapper;
    private final PilotRepository pilotRepository;
    private final RestTemplate restTemplate;
    private final PilotMapper pilotMapper;

    public PilotServiceImpl(ObjectMapper mapper, PilotRepository pilotRepository, RestTemplate restTemplate,
                            PilotMapper pilotMapper) {
        this.mapper = mapper;
        this.pilotRepository = pilotRepository;
        this.restTemplate = restTemplate;
        this.pilotMapper = pilotMapper;
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
            PilotDto newPilot =
                    PilotDto.builder().pilotName(pilotAuthPayload.name).pilotId(Long.parseLong(subject[2])).ownerHash(pilotAuthPayload.owner()).build();
            // TODO call the repository to save the pilot
            return newPilot;
        } catch (JsonProcessingException e) {
            throw new EsiParsingException(e, "savePilot", "PilotAuthPayload");
        }
    }

    @Override
    public List<PilotListInfo> getPilotsWithDetailsForUser(UUID userId, String token) {
        List<PilotListInfo> pilots = pilotRepository.findByEveAssistUser_UniqueUserOrderByNameAsc(userId,
                Pageable.ofSize(10));

        if (token != null) {
            HttpHeaders header = new HttpHeaders();
            header.setBearerAuth(token);

            for (PilotListInfo pilot : pilots) {
                PilotPublicDto publicData = restTemplate.exchange(
                        "https://localhost:8043/character/%s/public".formatted(pilot.getEvePilotId()),
                        HttpMethod.GET, new HttpEntity<>(header), PilotPublicDto.class).getBody();
                pilotMapper.partialUpdateDetails(publicData, pilot);
            }
        }
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
