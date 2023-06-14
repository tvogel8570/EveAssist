package com.eveassist.api.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

public interface SecurityPrincipal extends UserDetails, OAuth2User {
}
