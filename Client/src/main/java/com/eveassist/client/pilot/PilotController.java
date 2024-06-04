package com.eveassist.client.pilot;

import com.eveassist.client.pilot.dto.PilotAuthDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.ModelAndView;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("SameReturnValue")
@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/pilot")
public class PilotController {
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
    @Value("${pilot.token_uri}")
    private String pilotTokenUri;
    @Value("${pilot.scope}")
    private String pilotScope;
    @Value("${pilot.response_type}")
    private String pilotResponseType;

    @GetMapping("")
    public String index(@AuthenticationPrincipal DefaultOidcUser principal,
                        Model model) {
        model.addAttribute("pilots",
                pilotService.getPilotListForUser(UUID.fromString(principal.getName())));
        return "pilot/list";
    }

    @GetMapping("/refresh")
    public String refresh(@AuthenticationPrincipal DefaultOidcUser principal,
                          Authentication auth,
                          HttpServletRequest servletRequest,
                          @RequestParam(required = false, name = "pilotId") Long pilotId) {
        String token = getEaToken(auth, servletRequest);

        // get pilots data from ESI
        List<Long> requestedPilotIds = (pilotId == null) ? List.of() : List.of(pilotId);
        pilotService.updateUserPilotsFromEsi(UUID.fromString(principal.getSubject()), token, requestedPilotIds);

        return "redirect:/pilot";
    }

    @GetMapping("/create")
    public ModelAndView create() {
        String state = RandomStringUtils.randomAlphanumeric(10);
        String uri;

        uri = "redirect:%s?response_type=%s&redirect_uri=%s&client_id=%s&scope=%s&state=%s".formatted(
                pilotLoginUri,
                URLEncoder.encode(pilotResponseType, StandardCharsets.UTF_8),
                URLEncoder.encode(pilotRedirectUri, StandardCharsets.UTF_8),
                URLEncoder.encode(pilotClientId, StandardCharsets.UTF_8),
                URLEncoder.encode(pilotScope, StandardCharsets.UTF_8),
                URLEncoder.encode(state, StandardCharsets.UTF_8));

        return new ModelAndView(uri);
    }

    // redirect endpoint configured in Eve Online pilot client
    @GetMapping("/login")
    public String handleCcpLogin(
            @AuthenticationPrincipal DefaultOidcUser principal,
            @RequestParam String code,
            @RequestParam String state) {
        log.info("In handleCcpLogin code [{}] state [{}]", code, state);

        String credentials = new String(Base64.encodeBase64((pilotClientId + ":" + pilotClientSecret).getBytes()));
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "authorization_code");
        map.add("code", code);

        ResponseEntity<PilotAuthDto> response = RestClient.create().post()
                .uri(URI.create(pilotTokenUri))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", "Basic %s".formatted(credentials))
                .header("Host", "login.eveonline.com")
                .body(map)
                .retrieve().toEntity(PilotAuthDto.class);

        pilotService.createPilot(principal.getName(), response.getBody());

        return "redirect:/pilot/refresh";
    }

    @GetMapping("/{pilotId}/details")
    public String pilotDetails(@PathVariable Integer pilotId, Model model) {
        log.debug("pilotDetails pilotId[{}]", pilotId);


        model.addAttribute("pilotId", pilotId);


        return "pilot/details";
    }

    private String getEaToken(Authentication auth, HttpServletRequest servletRequest) {
        OAuth2AuthorizedClient authorizedClient = clientRepository.loadAuthorizedClient(
                "keycloak-confidential-user", auth, servletRequest);

        if (authorizedClient == null) {
            log.warn("Authorized client could not be loaded\nauth[{}]\nservlet[{}]", auth, servletRequest);
            return null;
        }

        String token = authorizedClient.getAccessToken().getTokenValue();
        log.debug("eaAuthToken is null [{}]", token == null);
        return token;
    }
}
