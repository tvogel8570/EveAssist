package com.eveassist.client.dashboard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/dashboard")
public class DashboardController {
    @GetMapping
    public String index(@AuthenticationPrincipal DefaultOidcUser principal, Model model) {
        model.addAttribute("name", principal.getName());
        model.addAttribute("preferred_name", principal.getAttribute("preferred_username"));
        return "dashboard/list";
    }
}
