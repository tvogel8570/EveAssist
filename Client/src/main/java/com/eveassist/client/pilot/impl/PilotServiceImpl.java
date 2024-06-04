package com.eveassist.client.pilot.impl;

import com.eveassist.client.core.exception.EaBusinessException;
import com.eveassist.client.exception.EsiParsingException;
import com.eveassist.client.pilot.PilotMapper;
import com.eveassist.client.pilot.PilotRepository;
import com.eveassist.client.pilot.PilotService;
import com.eveassist.client.pilot.dto.PilotAuthDto;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class PilotServiceImpl implements PilotService {
    public static final String ESI_CHAR_PUBLIC = "/character/%s/public";

    private final ObjectMapper jacksonMapper;
    private final PilotRepository pilotRepository;
    @Resource(name = "eaApiRestClient")
    private final RestClient eaApiRestClient;
    private final PilotMapper pilotMapper;
    private final EveAssistUserRepository eveAssistUserRepository;


    /**
     * save pilot created from data directly from Eve Online
     *
     * @param eveAssistUserId uniqueUser for Eve Assist User that owns pilot
     * @param pilotAuth       data directly from Eve Online authentication
     */


    @Override
    @Transactional
    public void createPilot(String eveAssistUserId, PilotAuthDto pilotAuth) {
        // TODO verify signature
        try {
            String[] chunks = pilotAuth.accessToken().split("\\.");
            String payload = new String(Base64.getUrlDecoder().decode(chunks[1]));
            PilotAuthPayload pilotAuthPayload = jacksonMapper.readValue(payload, PilotAuthPayload.class);

            // retrieve / build the components of a new pilot
            EveAssistUser eau = eveAssistUserRepository.findByUniqueUser(UUID.fromString(eveAssistUserId))
                    .orElseThrow(() -> new EaBusinessException(
                            "Could not find user with id of [%s]".formatted(eveAssistUserId)));
            Pilot newPilot = Pilot.PilotBuilder.aPilot()
                    .withName(pilotAuthPayload.name())
                    .withEvePilotId(Long.parseLong(pilotAuthPayload.pilotId()))
                    .withOwnerHash(pilotAuthPayload.owner())
                    .withEveAssistUser(eau)
                    .build();
            Token token = Token.TokenBuilder.aToken()
                    .withPilot(newPilot)
                    .withAccessToken(pilotAuth.accessToken().getBytes())
                    .withRefreshToken(pilotAuth.refreshToken().getBytes())
                    .build();
            Scope scope = Scope.ScopeBuilder.aScope().withToken(token).withPrivilege("test").build();

            // assemble the components into a valid pilot
            eau.addPilot(newPilot);
            newPilot.addToken(token);
            token.addScope(scope);

            // save
            pilotRepository.save(newPilot);
        } catch (JsonProcessingException e) {
            throw new EsiParsingException(e, "savePilot", "PilotAuthPayload");
        }
    }

    @Override
    public List<PilotListInfo> getPilotListForUser(UUID userId) {
        return pilotRepository.findByEveAssistUser_UniqueUserOrderByNameAsc(
                userId, Pageable.ofSize(10));
    }

    public PilotPublicDto getSinglePilotDetails(Long evePilotId, String eaAuthToken) {
        ResponseEntity<PilotPublicDto> publicData = eaApiRestClient.get()
                .uri(ESI_CHAR_PUBLIC.formatted(evePilotId))
                .header("Authorization", "Bearer %s".formatted(eaAuthToken))
                .retrieve()
                .toEntity(PilotPublicDto.class);
        if (publicData.getStatusCode().is2xxSuccessful())
            return publicData.getBody();

        log.warn("unable to get public data for pilot with Eve Pilot Id [{}]", evePilotId);
        return null;
    }

    @Override
    @Transactional
    public void updateUserPilotsFromEsi(UUID uniqueUser, String eaAuthToken, List<Long> requestedPilotIds) {

        // retrieve user from database
        EveAssistUser eveAssistUser = eveAssistUserRepository.findByUniqueUser(uniqueUser).orElseThrow(() ->
                new EaBusinessException("Could not find user with id of [%s]".formatted(uniqueUser)));

        // for each pilot
        for (Pilot pilot : eveAssistUser.getPilots()) {
            // check if refresh is requested
            if (requestedPilotIds.isEmpty() || requestedPilotIds.contains(pilot.getEvePilotId())) {
                // refresh pilot data from EaApi
                PilotPublicDto pilotDetails = this.getSinglePilotDetails(pilot.getEvePilotId(), eaAuthToken);
                pilotMapper.partialUpdateDetails(pilotDetails, pilot);
                log.debug("pilotDetails after partial update [%s]".formatted(pilotDetails));
                log.debug("pilot after partial update [%s]".formatted(pilot));
            }
        }

        // save user and pilot data to database
        eveAssistUserRepository.save(eveAssistUser);
    }
}
