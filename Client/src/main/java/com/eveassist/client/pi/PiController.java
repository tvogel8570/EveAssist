package com.eveassist.client.pi;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/pi")
public class PiController {
    @GetMapping
    public String index(Model model) {
        return "pi/list";
    }

    @GetMapping("/refresh")
    public ModelAndView refresh(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, ModelMap model) {
        return new ModelAndView("pi/pi-refresh", model);
    }
}
