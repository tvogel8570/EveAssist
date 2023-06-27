package com.eveassist.api.esi;

import com.eveassist.api.esi.dto.PilotPublicDto;
import com.eveassist.api.esi.response.*;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@RestController
public class EsiApiController {
    private final RestTemplate esiTemplate;
    private final CharactersMapper mapper;
    private final String esiBasePath = "https://esi.evetech.net/latest";
    private final String datasource = "tranquility";

    public EsiApiController(RestTemplate esiTemplate, CharactersMapper mapper) {
        this.esiTemplate = esiTemplate;
        this.mapper = mapper;
    }

    @GetMapping("/character/{pilotId}/public")
    public PilotPublicDto getPilotPublicData(@PathVariable Long pilotId) {
        log.debug("pilotId [{}]", pilotId);
        try {
            URL publicData = UriComponentsBuilder.fromHttpUrl(esiBasePath)
                    .queryParam("datasource", datasource)
                    .path("/characters/%d/".formatted(pilotId))
                    .build().toUri().toURL();
            log.debug("getPilotPublicData url: [{}]", publicData);
            URL portraitData = UriComponentsBuilder.fromHttpUrl(esiBasePath)
                    .queryParam("datasource", datasource)
                    .path("/characters/%d/portrait".formatted(pilotId))
                    .build().toUri().toURL();
            log.debug("getPilotPublicData url: [{}]", portraitData);
            URL affiliationData = UriComponentsBuilder.fromHttpUrl(esiBasePath)
                    .queryParam("datasource", datasource)
                    .path("/characters/affiliation")
                    .build().toUri().toURL();
            log.debug("getPilotPublicData url: [{}]", affiliationData);
            URL namesData = UriComponentsBuilder.fromHttpUrl(esiBasePath)
                    .queryParam("datasource", datasource)
                    .path("/universe/names")
                    .build().toUri().toURL();
            log.debug("getPilotPublicData url: [{}]", namesData);
            HttpEntity<MultiValueMap<String, String>> request = buildBaseRequest();
            CharactersDto charactersDto = esiTemplate
                    .exchange(publicData.toString(), HttpMethod.GET, request, CharactersDto.class).getBody();
            CharactersPortraitDto portraitDto = esiTemplate
                    .exchange(portraitData.toString(), HttpMethod.GET, request, CharactersPortraitDto.class).getBody();
            CharactersAffiliationDto[] affiliationDto = esiTemplate
                    .postForObject(affiliationData.toString(), List.of(pilotId), CharactersAffiliationDto[].class);
            UniverseNamesDto[] universeNamesDto = esiTemplate
                    .postForObject(namesData.toString(), this.validAffiliationIds(affiliationDto),
                            UniverseNamesDto[].class);

            if (affiliationDto == null || affiliationDto.length != 1)
                throw new RuntimeException("Unable to retrieve affiliation data from ESI");

            return mapper
                    .from(charactersDto, portraitDto, affiliationDto[0], this.lookupAffiliationDesc(universeNamesDto));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/character/{pilotId}/notifications")
    public CharactersNotificationsDto[] getNotifications(@PathVariable Long pilotId, @RequestParam String accessToken) {
        log.debug("accessToken [{}]", accessToken);
        log.debug("pilotId [{}]", pilotId);
        try {
            URL notificationData = UriComponentsBuilder.fromHttpUrl(esiBasePath)
                    .queryParam("datasource", datasource)
                    .path("/characters/%d/notifications".formatted(pilotId))
                    .build().toUri().toURL();
            log.debug("getNotifications url: [{}]", notificationData);
            HttpEntity<MultiValueMap<String, String>> request = buildBaseRequestWithBearerHeader(accessToken);
            return esiTemplate.exchange(notificationData.toString(), HttpMethod.GET, request,
                    CharactersNotificationsDto[].class).getBody();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private AffiliationDescDto lookupAffiliationDesc(@NotNull UniverseNamesDto[] namesDto) {
        AffiliationDescDto rtn = new AffiliationDescDto();
        for (UniverseNamesDto universeNamesDto : namesDto) {
            switch (universeNamesDto.category()) {
                case "alliance" -> rtn.setAllianceDesc(universeNamesDto.name());
                case "corporation" -> rtn.setCorporationDesc(universeNamesDto.name());
                case "faction" -> rtn.setFactionDesc(universeNamesDto.name());
                case "character" -> rtn.setCharacterDesc(universeNamesDto.name());
                default -> {
                }
            }
        }
        return rtn;
    }

    private List<Integer> validAffiliationIds(@NotNull CharactersAffiliationDto[] affiliationDto) {
        if (affiliationDto != null && affiliationDto.length == 1) {
            CharactersAffiliationDto match = affiliationDto[0];
            return Stream.of(match.allianceId(), match.corporationId(), match.factionId(), match.characterId())
                    .filter(Objects::nonNull)
                    .toList();
        }

        return new ArrayList<>();
    }

    private HttpEntity<MultiValueMap<String, String>> buildBaseRequest() {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.valueOf(MediaType.APPLICATION_JSON_VALUE)));
        headers.setCacheControl("no-cache");
        headers.add(HttpHeaders.USER_AGENT, "EveAssist/0.1 Where Am I");

        return new HttpEntity<>(map, headers);
    }

    private HttpEntity<MultiValueMap<String, String>> buildBaseRequestWithBearerHeader(String token) {
        HttpEntity<MultiValueMap<String, String>> baseRequest = this.buildBaseRequest();
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(baseRequest.getHeaders());
        headers.setBearerAuth(token);

        return new HttpEntity<>(baseRequest.getBody(), headers);
    }
}
