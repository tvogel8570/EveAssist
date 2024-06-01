package com.eveassist.client.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/")
public class HomeController {

    @GetMapping("")
    public String index(@AuthenticationPrincipal DefaultOidcUser principal) {


        if (principal != null)
            return "redirect:dashboard";

        return "index";
    }
}
