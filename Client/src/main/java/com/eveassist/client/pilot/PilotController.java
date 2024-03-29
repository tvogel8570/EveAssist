package com.eveassist.client.pilot;

import com.eveassist.client.pilot.dto.PilotAuthDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.ModelAndView;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/pilot")
public class PilotController {
    private final RestTemplate restTemplate;
    private final PilotService pilotService;
    private final OAuth2AuthorizedClientRepository clientRepository;

    @Value("${pilot.client_id}")
    private String pilotClientId;
    @Value("${pilot.client_secret}")
    private String pilotClientSecret;
    @Value("${pilot.redirect_uri}")
    private String pilotRedirectUri;
    @Value("${pilot.login_uri}")
    private String pilotLoginUri;
    @Value("${pilot.scope}")
    private String pilotScope;
    @Value("${pilot.response_type}")
    private String pilotResponseType;

    public PilotController(RestTemplate restTemplate, PilotService pilotService,
                           OAuth2AuthorizedClientRepository clientRepository) {
        this.restTemplate = restTemplate;
        this.pilotService = pilotService;
        this.clientRepository = clientRepository;
    }

    @GetMapping("")
    public String index(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal,
                        Authentication auth,
                        HttpServletRequest servletRequest,
                        Model model) {
        OAuth2AuthorizedClient accessToken = clientRepository.loadAuthorizedClient("keycloak-confidential-user",
                auth, servletRequest);

        String token = accessToken == null ? "null" : accessToken.getAccessToken().getTokenValue();
        log.debug("accessToken is null [{}]", accessToken == null);
        model.addAttribute("pilots",
                pilotService.getPilotsWithDetailsForUser(UUID.fromString(principal.getName()),
                        token));
        return "pilot/list";
    }

    @GetMapping("/create")
    public ModelAndView create(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, ModelMap model) {
        String state = RandomStringUtils.randomAlphanumeric(10);
        String uri;

        uri = "redirect:%s?response_type=%s&redirect_uri=%s&client_id=%s&scope=%s&state=%s".formatted(
                pilotLoginUri,
                pilotResponseType,
                URLEncoder.encode(pilotRedirectUri, StandardCharsets.UTF_8),
                pilotClientId,
                URLEncoder.encode(pilotScope, StandardCharsets.UTF_8),
                state);
        this.pilotService.linkState(principal.getName(), state);
        return new ModelAndView(uri, model);
    }

    @GetMapping("/login")
    public String handleCcpLogin(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal,
                                 @RequestParam String code, @RequestParam String state) {
        log.info("In handleCcpLogin code [{}] state [{}]", code, state);
        this.pilotService.getUserFromState(state);
        String credentials = new String(Base64.encodeBase64((pilotClientId + ":" + pilotClientSecret).getBytes()));
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

        pilotService.savePilot(principal.getName(), response.getBody());

        return "redirect:/pilot";
    }

    @GetMapping("/{pilotId}/details")
    public String pilotDetails(@PathVariable Integer pilotId, Model model) {
        log.debug("pilotDetails pilotId[{}]", pilotId);


        model.addAttribute("pilotId", pilotId);


        return "pilot/details";
    }
}