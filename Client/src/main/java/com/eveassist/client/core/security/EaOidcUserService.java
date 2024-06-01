package com.eveassist.client.core.security;

import com.eveassist.client.user.EveAssistUserService;
import com.eveassist.client.user.dto.EveAssistUserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class EaOidcUserService extends OidcUserService {
    private final EveAssistUserService userApi;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        log.info("inside custom DefaultOAuth2UserService [{}] ", oidcUser.toString());

        // check if user is authenticated and if so does user exist in snc_user table
        if (oidcUser instanceof DefaultOidcUser) {
            UUID userUnique = UUID.fromString(oidcUser.getSubject());

            if (!userApi.existsUserByUniqueUser(userUnique))
                addEaUser(oidcUser, userUnique);
        }
        return oidcUser;
    }

    private void addEaUser(OidcUser oidcUser, UUID userUnique) {
        String userName = oidcUser.getAttributes().get("preferred_username").toString();
        String email = oidcUser.getAttributes().get("email").toString();
        if (userName == null || email == null) {
            log.warn("username or email attributes were not set for user [{}]", oidcUser);
            return;
        }

        userApi.addUser(EveAssistUserDto.EveAssistUserDtoBuilder.anEveAssistUserDto()
                .withUniqueUser(userUnique)
                .withUserName(userName)
                .withEmail(email)
                .build());
        log.info("user [{}] with unique id of [{}] has been added to the user registry", userName, userUnique);
    }
}
