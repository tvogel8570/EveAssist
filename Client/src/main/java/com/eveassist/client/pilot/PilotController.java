package com.eveassist.client.pilot;

import com.eveassist.client.config.PilotProps;
import com.eveassist.client.pilot.dto.PilotAuthDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
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
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("SameReturnValue")
@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/pilot")
public class PilotController {
    private static final String REGISTRATION_ID = "keycloak-confidential-user";

    private final PilotService pilotService;
    private final OAuth2AuthorizedClientRepository clientRepository;
    private final PilotProps pilot;
    private final OAuth2AuthorizedClientManager authorizedClientManager;

    @GetMapping("")
    public String index(@AuthenticationPrincipal(expression = "name") String principalName,
                        Model model) {
        model.addAttribute("pilots",
                pilotService.getPilotListForUser(UUID.fromString(principalName)));
        return "pilot/list";
    }

    @GetMapping("/refresh")
    public String refresh(@AuthenticationPrincipal(expression = "name") String principalName,
                          @RequestParam(required = false, name = "pilotId") Long pilotId) {
        String token = this.getBearer(REGISTRATION_ID).orElseThrow();

        // get pilots data from ESI
        List<Long> requestedPilotIds = (pilotId == null) ? List.of() : List.of(pilotId);
        pilotService.updateUserPilotsFromEsi(UUID.fromString(principalName), token, requestedPilotIds);

        return "redirect:/pilot";
    }

    @GetMapping("/create")
    public ModelAndView create() {
        String state = RandomStringUtils.randomAlphanumeric(10);
        String uri;

        uri = "redirect:%s?responseType=%s&redirectUri=%s&client_id=%s&scope=%s&state=%s".formatted(
                pilot.loginUri(),
                URLEncoder.encode(pilot.responseType(), StandardCharsets.UTF_8),
                URLEncoder.encode(pilot.redirectUri(), StandardCharsets.UTF_8),
                URLEncoder.encode(pilot.clientId(), StandardCharsets.UTF_8),
                URLEncoder.encode(pilot.scopeAsString(), StandardCharsets.UTF_8),
                URLEncoder.encode(state, StandardCharsets.UTF_8));

        return new ModelAndView(uri);
    }

    // redirect endpoint configured in Eve Online pilot client
    @GetMapping("/login")
    public String handleCcpLogin(
            @AuthenticationPrincipal(expression = "name") String principalName,
            @RequestParam String code,
            @RequestParam String state) {
        log.info("In handleCcpLogin code [{}] state [{}]", code, state);

        String credentials = new String(Base64.encodeBase64((pilot.clientId() + ":" + pilot.clientSecret()).getBytes()));
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "authorization_code");
        map.add("code", code);

        ResponseEntity<PilotAuthDto> response = RestClient.create().post()
                .uri(URI.create(pilot.tokenUri()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", "Basic %s".formatted(credentials))
                .header("Host", "login.eveonline.com")
                .body(map)
                .retrieve().toEntity(PilotAuthDto.class);

        pilotService.createPilot(principalName, response.getBody());

        return "redirect:/pilot/refresh";
    }

    @GetMapping("/{pilotId}/details")
    public String pilotDetails(@PathVariable Integer pilotId, Model model) {
        log.debug("pilotDetails pilotId[{}]", pilotId);


        model.addAttribute("pilotId", pilotId);


        return "pilot/details";
    }

    public Optional<String> getBearer(String registrationId) {
        final var authentication = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication()).orElse(
                new AnonymousAuthenticationToken(
                        "anonymous",
                        "anonymous",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
                .principal(authentication).build();
        final var authorizedClient = Optional.ofNullable(authorizedClientManager.authorize(authorizeRequest));
        final var token = authorizedClient.map(OAuth2AuthorizedClient::getAccessToken);
        return token.map(OAuth2AccessToken::getTokenValue);
    }
}
