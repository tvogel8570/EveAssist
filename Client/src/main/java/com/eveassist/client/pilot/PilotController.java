package com.eveassist.client.pilot;

import com.eveassist.client.pilot.dto.PilotAuthDto;
import com.eveassist.client.pilot.dto.PilotDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Controller
@RequestMapping("/pilot")
@Slf4j
public class PilotController {
    private final RestTemplate restTemplate;
    private final PilotService pilotService;
    @Value("${esi.pilot.client.id}")
    private String eveId;
    @Value("${esi.pilot.client.secret}")
    private String eveSecret;


    public PilotController(RestTemplate restTemplate, PilotService pilotService) {
        this.restTemplate = restTemplate;
        this.pilotService = pilotService;
    }

    @GetMapping("/login")
    @ResponseBody
    String handleCcpGetResponse(@RequestParam String code) {
        log.info("In handleCcpGetResponse code [{}]", code);
        String credentials = new String(Base64.encodeBase64((eveId + ":" + eveSecret).getBytes()));
        URI uri = URI.create("https://login.eveonline.com/v2/oauth/token");
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(credentials);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Host", "login.eveonline.com");

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "authorization_code");
        map.add("code", code);
        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(map, headers);
        ResponseEntity<PilotAuthDto> response = restTemplate.exchange(uri, HttpMethod.POST,
                request, PilotAuthDto.class);

        PilotDto dto = pilotService.savePilot(response.getBody());

        return "Pilot : %d - %s was saved.  You may close this browser tab".formatted(dto.pilotId(), dto.pilotName());
    }
}