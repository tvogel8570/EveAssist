package com.eveassist.identity.internal.web;

import com.eveassist.identity.internal.SiteInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Pages for anyone, signed in or not: the privacy policy and security.txt (RFC 9116). */
@Controller
class PublicPagesController {

    private final SiteInfo site;
    private final Clock clock;

    PublicPagesController(SiteInfo site, Clock clock) {
        this.site = site;
        this.clock = clock;
    }

    @GetMapping("/privacy")
    String privacy(Model model) {
        // for the mailto: link: Thymeleaf does not allow a bean reference inside a URL attribute
        model.addAttribute("contactEmail", site.getContactEmail());
        return "privacy";
    }

    /** Expires is a year ahead each time it is asked, so it can never lapse; RFC 9116 wants one that is less than a year away. */
    @GetMapping("/.well-known/security.txt")
    ResponseEntity<String> securityTxt(HttpServletRequest request) {
        var base = ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
        var text = """
                Contact: mailto:%s
                Expires: %s
                Preferred-Languages: en
                Canonical: %s/.well-known/security.txt
                Policy: %s/privacy#incidents
                """.formatted(site.getContactEmail(), clock.instant().plus(Duration.ofDays(364)), base, base);
        return ResponseEntity.ok().contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
                .body(text);
    }
}
