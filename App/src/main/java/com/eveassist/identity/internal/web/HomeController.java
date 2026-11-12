package com.eveassist.identity.internal.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class HomeController {

    /** {@code deleted} is set by the redirect after an account deletion: done, or partial when Keycloak could not be asked. */
    @GetMapping("/")
    String home(@RequestParam(required = false) String deleted, Model model) {
        if ("done".equals(deleted) || "partial".equals(deleted)) {
            model.addAttribute("deleted", deleted);
        }
        return "home";
    }
}
