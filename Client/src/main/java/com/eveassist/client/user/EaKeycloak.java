package com.eveassist.client.user;


import com.eveassist.client.core.exception.EaBusinessException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

@Slf4j
@Component
public class EaKeycloak {
    private final static String KEYCLOAK_AUTH = "%s/realms/%s/protocol/openid-connect/token";
    private final static String KEYCLOAK_QUERY_USER = "/admin/realms/%s/users/%s";
    private final static String KEYCLOAK_QUERY_GROUPS = "/admin/realms/%s/groups";
    private final static String KEYCLOAK_ADD_GROUP = "/admin/realms/%s/users/%s/groups/%s";
    private final static String KEYCLOAK_DELETE_GROUP = "/admin/realms/%s/users/%s/groups/%s";
    private final static String KEYCLOAK_ADD_USER = "/admin/realms/%s/users";
    private final static String KEYCLOAK_DELETE_USER = "/admin/realms/%s/users/%s";

    private final RestClient keycloakRestClient;
    private final Map<String, String> groupIds = new HashMap<>();
    private final MultiValueMap<String, String> authBody = new LinkedMultiValueMap<>();
    private final String baseUrl;
    private final String realm;
    private final ObjectMapper mapper = new ObjectMapper();
    private AuthResponse authResponse;


    public EaKeycloak(
            @Value("${keycloak-base-url}") String baseUrl,
            @Value("${keycloak-realm}") String realm,
            @Value("${keycloak.eveassist.id}") String client_id,
            @Value("${keycloak.eveassist.password}") String client_secret) {
        this.keycloakRestClient = RestClient.create(baseUrl);
        this.realm = realm;
        this.baseUrl = baseUrl;
        authBody.add("grant_type", "client_credentials");
        authBody.add("client_id", client_id);
        authBody.add("client_secret", client_secret);
        this.queryGroups();
    }

    public void addUserToGroup(UUID uniqueUser, String groupName) {
        this.authClient();
        String groupId = this.groupIds.get(groupName);
        if (groupId == null)
            throw new EaBusinessException("Group not found [%s]".formatted(groupName));
        if (!userExists(uniqueUser))
            throw new EaBusinessException("User not found [%s]".formatted(uniqueUser));
        ResponseEntity<String> groupAddResponse = keycloakRestClient.put()
                .uri(String.format(KEYCLOAK_ADD_GROUP, realm, uniqueUser, groupId))
                .header("Authorization", "Bearer " + authResponse.access_token)
                .retrieve()
                .toEntity(String.class);
        if (!groupAddResponse.getStatusCode().is2xxSuccessful())
            throw new EaBusinessException(
                    "Could not add group [%s] to unique user of [%s]".formatted(groupName, uniqueUser));
    }

    public void removeUserFromGroup(UUID uniqueUser, String groupName) {
        this.authClient();
        String groupId = this.groupIds.get(groupName);
        if (groupId == null)
            throw new EaBusinessException("Group not found [%s]".formatted(groupName));
        if (!userExists(uniqueUser))
            throw new EaBusinessException("User not found [%s]".formatted(uniqueUser));
        ResponseEntity<String> groupAddResponse = keycloakRestClient.delete()
                .uri(String.format(KEYCLOAK_DELETE_GROUP, realm, uniqueUser, groupId))
                .header("Authorization", "Bearer " + authResponse.access_token)
                .retrieve()
                .toEntity(String.class);
        if (!groupAddResponse.getStatusCode().is2xxSuccessful())
            throw new EaBusinessException(
                    "Could not delete group [%s] from unique user of [%s]".formatted(groupName, uniqueUser));
    }

    public UUID addUser(String userName, String email) {
        this.authClient();
        if (StringUtils.isBlank(userName) || StringUtils.isBlank(email))
            throw new EaBusinessException("Username or email is empty");

        String newUser;
        try {
            newUser = mapper.writeValueAsString(new CreateUser(userName, email));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        ResponseEntity<String> newUserResponse = keycloakRestClient.post()
                .uri(String.format(KEYCLOAK_ADD_USER, realm))
                .header("Authorization", "Bearer " + authResponse.access_token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(newUser)
                .retrieve()
                .toEntity(String.class);
        if (!newUserResponse.getStatusCode().is2xxSuccessful()) {
            throw new EaBusinessException("Could not add user [%s]".formatted(newUser));
        }
        List<String> location = newUserResponse.getHeaders().get("Location");

        if ((location == null) || (location.size() != 1))
            throw new EaBusinessException("Error getting created user id");
        try {
            URL locationUrl = new URL(location.get(0));
            String path = locationUrl.getPath();
            return UUID.fromString(path.substring(path.lastIndexOf("/") + 1));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeUser(UUID uniqueUser) {
        this.authClient();
        if (uniqueUser == null)
            throw new EaBusinessException("UUID must not be null");

        ResponseEntity<String> deleteUserResponse = keycloakRestClient.delete()
                .uri(String.format(KEYCLOAK_DELETE_USER, realm, uniqueUser))
                .header("Authorization", "Bearer " + authResponse.access_token)
                .retrieve()
                .toEntity(String.class);
        if (!deleteUserResponse.getStatusCode().is2xxSuccessful())
            throw new EaBusinessException("Could not delete user with id of [%s]".formatted(uniqueUser));
    }


    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean userExists(UUID uniqueUser) {
        this.authClient();
        ResponseEntity<String> userResponse = keycloakRestClient.get()
                .uri(String.format(KEYCLOAK_QUERY_USER, realm, uniqueUser))
                .header("Authorization", "Bearer " + authResponse.access_token)
                .retrieve()
                .toEntity(String.class);
        return userResponse.getStatusCode().is2xxSuccessful();
    }

    void queryGroups() {
        this.authClient();
        ResponseEntity<GroupInfo[]> groupInfos = keycloakRestClient.get()
                .uri(String.format(KEYCLOAK_QUERY_GROUPS, realm))
                .header("Authorization", "Bearer " + authResponse.access_token)
                .retrieve()
                .toEntity(GroupInfo[].class);
        if (!groupInfos.getStatusCode().is2xxSuccessful()) {
            throw new EaBusinessException("Error retrieving groups from keycloak [%s]".formatted(realm));
        }
        for (GroupInfo groupInfo : Objects.requireNonNull(groupInfos.getBody(), "No group information")) {
            groupIds.put(groupInfo.name, groupInfo.id);
        }
    }

    void authClient() {
        ResponseEntity<AuthResponse> authResponse = keycloakRestClient.post()
                .uri(String.format(KEYCLOAK_AUTH, baseUrl, realm))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(authBody)
                .retrieve()
                .toEntity(AuthResponse.class);

        if (!authResponse.getStatusCode().is2xxSuccessful()) {
            String msg = "Keycloak authentication failed [%s]".formatted(authResponse.getStatusCode());
            log.error(msg);
            throw new EaBusinessException(msg);
        }
        this.authResponse = authResponse.getBody();
    }

    private record AuthResponse(
            String access_token,
            String token_type,
            String expires_in,
            String scope,
            String refresh_expires_in,
            @JsonProperty("not-before-policy")
            String not_before_policy) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GroupInfo(
            String id,
            String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CreateUser(
            String username,
            String email) {
    }
}
