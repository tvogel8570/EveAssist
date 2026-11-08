package com.eveassist.identity.internal.web;

import com.eveassist.identity.internal.AccountService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class AccountController {

    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/account")
    String account(@AuthenticationPrincipal OidcUser user, Model model) {
        model.addAttribute("account", accounts.findByKeycloakSub(user.getSubject()).orElseThrow());
        model.addAttribute("roles", user.getAuthorities().stream().map(Object::toString).sorted().toList());
        return "account";
    }
}
