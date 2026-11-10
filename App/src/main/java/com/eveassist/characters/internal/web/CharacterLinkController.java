package com.eveassist.characters.internal.web;

import com.eveassist.characters.internal.CharacterLinking;
import com.eveassist.identity.Viewer;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The two addresses of the linking flow. Both are plain redirects, so they need no page of their own. */
@Controller
@PreAuthorize("hasRole('USER')")
class CharacterLinkController {

    private static final String STATE = "characters.link.state";

    private final CharacterLinking linking;

    CharacterLinkController(CharacterLinking linking) {
        this.linking = linking;
    }

    @GetMapping("/characters/link")
    String start(Viewer viewer, HttpSession session) {
        var start = linking.start(viewer);
        if (start.isEmpty()) {
            return "redirect:/characters?linking=no-feature";
        }
        session.setAttribute(STATE, start.get().state());
        return "redirect:" + start.get().authorizeUrl();
    }

    @GetMapping("/characters/link/callback")
    String callback(Viewer viewer, HttpSession session, @RequestParam(required = false) String code,
            @RequestParam(required = false) String state, @RequestParam(required = false) String error) {
        var expected = (String) session.getAttribute(STATE);
        session.removeAttribute(STATE);
        if (error != null || code == null) {
            return "redirect:/characters?linking=declined";
        }
        return "redirect:/characters?linking=" + linking.complete(viewer, expected, state, code);
    }
}
