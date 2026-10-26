package com.eveassist.api.esi;

import com.eveassist.api.esi.dto.PilotPublicDto;
import com.eveassist.api.esi.exception.EsiParameterException;
import com.eveassist.api.esi.exception.InvalidUrlException;
import com.eveassist.api.esi.response.*;
import com.eveassist.api.sde.chr.ChrDao;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
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
    private static final String ESI_BASE_PATH = "https://esi.evetech.net/latest";
    private static final String DATASOURCE = "tranquility";
    private final RestClient esiClient;
    private final CharactersMapper mapper;
    private final ChrDao chrDao;


    public EsiApiController(RestClient esiClient, CharactersMapper mapper, ChrDao chrDao) {
        this.esiClient = esiClient;
        this.mapper = mapper;
        this.chrDao = chrDao;
    }

    @GetMapping("/character/{pilotId}/public")
    public PilotPublicDto getPilotPublicData(@PathVariable Long pilotId) {
        final String METHOD_NAME = "getPilotPublicData";
        log.debug("pilotId [{}]", pilotId);
        try {
            URL publicData = UriComponentsBuilder.fromUriString(ESI_BASE_PATH)
                    .queryParam("datasource", DATASOURCE)
                    .path("/characters/%d/".formatted(pilotId))
                    .build().toUri().toURL();
            log.debug("getPilotPublicData url: [{}]", publicData);
            URL portraitData = UriComponentsBuilder.fromUriString(ESI_BASE_PATH)
                    .queryParam("datasource", DATASOURCE)
                    .path("/characters/%d/portrait".formatted(pilotId))
                    .build().toUri().toURL();
            log.debug("getPilotPublicData url: [{}]", portraitData);
            URL affiliationData = UriComponentsBuilder.fromUriString(ESI_BASE_PATH)
                    .queryParam("datasource", DATASOURCE)
                    .path("/characters/affiliation")
                    .build().toUri().toURL();
            log.debug("getPilotPublicData url: [{}]", affiliationData);
            URL namesData = UriComponentsBuilder.fromUriString(ESI_BASE_PATH)
                    .queryParam("datasource", DATASOURCE)
                    .path("/universe/names")
                    .build().toUri().toURL();
            log.debug("getPilotPublicData url: [{}]", namesData);
            CharactersDto charactersDto = esiClient.get().uri(publicData.toString())
                    .retrieve().body(CharactersDto.class);
            CharactersPortraitDto portraitDto = esiClient.get().uri(portraitData.toString())
                    .retrieve().body(CharactersPortraitDto.class);
            CharactersAffiliationDto[] affiliationDto = esiClient.post().uri(affiliationData.toString())
                    .body(List.of(pilotId))
                    .retrieve().body(CharactersAffiliationDto[].class);
            UniverseNamesDto[] universeNamesDto = esiClient.post().uri(namesData.toString())
                    .body(this.validAffiliationIds(affiliationDto))
                    .retrieve().body(UniverseNamesDto[].class);

            if (affiliationDto == null || affiliationDto.length != 1)
                throw new EsiParameterException("Unable to retrieve affiliation data from ESI");

            return mapper.from(charactersDto, portraitDto, affiliationDto[0],
                    this.lookupAffiliationDesc(universeNamesDto, charactersDto));
        } catch (MalformedURLException e) {
            throw new InvalidUrlException(e, METHOD_NAME, "multiple URLs");
        }
    }

    @GetMapping("/character/{pilotId}/notifications")
    public CharactersNotificationsDto[] getNotifications(@PathVariable Long pilotId, @RequestParam String accessToken) {
        final String METHOD_NAME = "getNotifications";
        log.debug("accessToken [{}]", accessToken);
        log.debug("pilotId [{}]", pilotId);
        try {
            URL notificationData = UriComponentsBuilder.fromUriString(ESI_BASE_PATH)
                    .queryParam("datasource", DATASOURCE)
                    .path("/characters/%d/notifications".formatted(pilotId))
                    .build().toUri().toURL();
            log.debug("getNotifications url: [{}]", notificationData);
            return esiClient.get().uri(notificationData.toString())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve().body(CharactersNotificationsDto[].class);
        } catch (MalformedURLException e) {
            throw new InvalidUrlException(e, METHOD_NAME, "/characters/%d/notifications".formatted(pilotId));
        }
    }

    private AffiliationDescDto lookupAffiliationDesc(@NotNull UniverseNamesDto[] namesDto,
                                                     @NotNull CharactersDto charactersDto) {
        AffiliationDescDto rtn = new AffiliationDescDto();
        for (UniverseNamesDto universeNamesDto : namesDto) {
            switch (universeNamesDto.category()) {
                case "alliance" -> rtn.setAllianceDesc(universeNamesDto.name());
                case "corporation" -> rtn.setCorporationDesc(universeNamesDto.name());
                case "faction" -> rtn.setFactionDesc(universeNamesDto.name());
                case "character" -> rtn.setCharacterDesc(universeNamesDto.name());
                default -> throw new EsiParameterException("new constant in affiliation %s - %s"
                        .formatted(universeNamesDto.category(), universeNamesDto.name()));
            }
            rtn.setRaceDesc(chrDao.getRace(charactersDto.raceId()).getRaceName());
            rtn.setBloodlineDesc(chrDao.getBloodline(charactersDto.bloodlineId()).getBloodlineName());
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
}
