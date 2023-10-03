package com.eveassist.client.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
public class HomeController {

    @GetMapping("")
    public String index(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal, Model model) {

        if (principal != null)
            return "redirect:dashboard";

        return "index";
    }
}
