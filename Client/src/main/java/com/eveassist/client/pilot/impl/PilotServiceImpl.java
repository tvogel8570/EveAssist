package com.eveassist.client.pilot.impl;

import com.eveassist.client.exception.EsiParsingException;
import com.eveassist.client.pilot.PilotMapper;
import com.eveassist.client.pilot.PilotRepository;
import com.eveassist.client.pilot.PilotService;
import com.eveassist.client.pilot.dto.PilotAuthDto;
import com.eveassist.client.pilot.dto.PilotDto;
import com.eveassist.client.pilot.dto.PilotListInfo;
import com.eveassist.client.pilot.dto.PilotPublicDto;
import com.eveassist.client.pilot.entity.Pilot;
import com.eveassist.client.pilot.entity.Scope;
import com.eveassist.client.pilot.entity.Token;
import com.eveassist.client.user.EveAssistUserRepository;
import com.eveassist.client.user.entity.EveAssistUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class PilotServiceImpl implements PilotService {
    public static final String ESI_CHAR_PUBLIC = "/character/%s/public";

    private final ObjectMapper mapper;
    private final PilotRepository pilotRepository;
    @Resource(name = "eaApiRestClient")
    private final RestClient eaApiRestClient;
    private final PilotMapper pilotMapper;
    private final EveAssistUserRepository eveAssistUserRepository;

    @Override
    public PilotDto savePilot(String eveAssistUserId, PilotAuthDto pilotAuth) {
        String[] chunks = pilotAuth.accessToken().split("\\.");

        // TODO verify signature

        Base64.Decoder decoder = Base64.getUrlDecoder();
        String payload = new String(decoder.decode(chunks[1]));

        try {
            PilotAuthPayload pilotAuthPayload = mapper.readValue(payload, PilotAuthPayload.class);
            String[] subject = pilotAuthPayload.sub().split(":");
            EveAssistUser eau = eveAssistUserRepository.findByUniqueUser(UUID.fromString(eveAssistUserId));
            Pilot newPilot = Pilot.PilotBuilder.aPilot()
                    .withName(pilotAuthPayload.name())
                    .withEvePilotId(Long.parseLong(subject[2]))
                    .withOwnerHash(pilotAuthPayload.owner())
                    .withEveAssistUser(eau)
                    .build();
            Token token = Token.TokenBuilder.aToken()
                    .withPilot(newPilot)
                    .withAccessToken(pilotAuth.accessToken().getBytes())
                    .withRefreshToken(pilotAuth.refreshToken().getBytes())
                    .build();
            Scope scope = Scope.ScopeBuilder.aScope().withToken(token).withPrivilege("test").build();
            token.setScopes(Set.of(scope));
            newPilot.setToken(Set.of(token));
            pilotRepository.save(newPilot);
            return pilotMapper.toDto(newPilot);
        } catch (JsonProcessingException e) {
            throw new EsiParsingException(e, "savePilot", "PilotAuthPayload");
        }
    }

    @Override
    public List<PilotListInfo> getPilotsWithDetailsForUser(UUID userId, String token) {
        List<PilotListInfo> pilots = pilotRepository.findByEveAssistUser_UniqueUserOrderByNameAsc(userId,
                Pageable.ofSize(10));

        if (token != null) {
            for (PilotListInfo pilot : pilots) {
                ResponseEntity<PilotPublicDto> publicData = eaApiRestClient.get()
                        .uri(ESI_CHAR_PUBLIC.formatted(pilot.getEvePilotId()))
                        .header("Authorization", "Bearer %s".formatted(token))
                        .retrieve()
                        .toEntity(PilotPublicDto.class);
                if (publicData.getStatusCode().is2xxSuccessful()) {
                    pilotMapper.partialUpdateDetails(publicData.getBody(), pilot);
                } else {
                    log.warn("unable to get public data for pilot with Eve Pilot Id [{}]", pilot.getEvePilotId());
                }
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

}
