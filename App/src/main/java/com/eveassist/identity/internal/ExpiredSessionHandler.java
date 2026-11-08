package com.eveassist.identity.internal;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;

/**
 * Sends a signed-out request to sign-in. An htmx request is told to go there, so the sign-in page is not swapped into a
 * fragment. An ended session usually shows up as a refused CSRF token, before the request is seen as signed out.
 */
class ExpiredSessionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final String signInPath;
    private final AccessDeniedHandler otherwise = new AccessDeniedHandlerImpl();

    ExpiredSessionHandler(String signInPath) {
        this.signInPath = signInPath;
    }

    private static boolean isHtmxRequest(HttpServletRequest request) {
        return request.getHeader("HX-Request") != null;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        if (isHtmxRequest(request)) {
            redirect(response);
        } else {
            response.sendRedirect(signInPath);
        }
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException, ServletException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean signedOut = authentication == null || authentication instanceof AnonymousAuthenticationToken;
        if (isHtmxRequest(request) && signedOut) {
            redirect(response);
        } else {
            otherwise.handle(request, response, e);
        }
    }

    private void redirect(HttpServletResponse response) {
        response.setHeader("HX-Redirect", signInPath);
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
