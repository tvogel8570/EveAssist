package com.eveassist.identity.internal;

import com.eveassist.identity.Viewer;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedCredentialsNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Lets any page controller declare a {@link Viewer} parameter without depending on Spring Security. */
@Component
class ViewerArgumentResolver implements HandlerMethodArgumentResolver {

    private final AccountService accounts;

    ViewerArgumentResolver(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(Viewer.class);
    }

    @Override
    public Viewer resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser user)) {
            throw new PreAuthenticatedCredentialsNotFoundException("No signed-in account");
        }
        return accounts.findByKeycloakSub(user.getSubject())
                .map(account -> new Viewer(account.id()))
                .orElseThrow(() -> new PreAuthenticatedCredentialsNotFoundException("Account not found"));
    }
}
