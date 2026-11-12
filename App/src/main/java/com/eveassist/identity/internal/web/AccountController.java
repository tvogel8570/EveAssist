package com.eveassist.identity.internal.web;

import com.eveassist.identity.internal.AccountData;
import com.eveassist.identity.internal.AccountDeletion;
import com.eveassist.identity.internal.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tools.jackson.databind.json.JsonMapper;

@Controller
class AccountController {

    private final AccountService accounts;
    private final AccountData data;
    private final AccountDeletion deletion;
    private final JsonMapper json;

    AccountController(AccountService accounts, AccountData data, AccountDeletion deletion, JsonMapper json) {
        this.accounts = accounts;
        this.data = data;
        this.deletion = deletion;
        this.json = json;
    }

    @GetMapping("/account")
    String account(@AuthenticationPrincipal OidcUser user, Model model) {
        model.addAttribute("account", accounts.findByKeycloakSub(user.getSubject()).orElseThrow());
        model.addAttribute("roles", user.getAuthorities().stream().map(Object::toString).sorted().toList());
        return "account";
    }

    /** Everything the account holds that the user may take with them, as one JSON file. Never a token. */
    @GetMapping("/account/export")
    ResponseEntity<byte[]> export(@AuthenticationPrincipal OidcUser user) {
        var account = accounts.findByKeycloakSub(user.getSubject()).orElseThrow();
        var body = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(data.export(account));
        var name = "eveassist-export-" + LocalDate.now(ZoneOffset.UTC) + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    /** What deleting would remove, and the button. Nothing is deleted by opening this page. */
    @GetMapping("/account/delete")
    String confirmDelete(@AuthenticationPrincipal OidcUser user, Model model) {
        return showDeletion(user, model, false);
    }

    @PostMapping("/account/delete")
    String delete(@AuthenticationPrincipal OidcUser user, @RequestParam(defaultValue = "false") boolean confirm,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        if (!confirm) {
            response.setStatus(422);
            return showDeletion(user, model, true);
        }
        var result = deletion.delete(user.getSubject());
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/?deleted=" + (result == AccountDeletion.Result.DELETED ? "done" : "partial");
    }

    private String showDeletion(OidcUser user, Model model, boolean notConfirmed) {
        var account = accounts.findByKeycloakSub(user.getSubject()).orElseThrow();
        model.addAttribute("account", account);
        model.addAttribute("counts", data.counts(account));
        model.addAttribute("notConfirmed", notConfirmed);
        return "account-delete";
    }
}
