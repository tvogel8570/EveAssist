package com.eveassist.client.user;

import com.eveassist.client.user.response.LoginFormResponse;
import com.eveassist.client.user.response.RegistrationFormResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/user")
public class UserController {
    private final EveAssistUserService userService;

    @GetMapping("/create")
    public String createRegistrationForm(Model model) {
        model.addAttribute("user", new RegistrationFormResponse());
        return "/user/registration";
    }

    @GetMapping("/login")
    public String loginRegister(Model model) {
        model.addAttribute("user", new LoginFormResponse());
        return "/user/login";
    }

    @PostMapping("/doLogin")
    public String login(
            @Valid @ModelAttribute("user") LoginFormResponse loginFormResponse,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors())
            return "user/login";
        if (userService.login(loginFormResponse))
            return "dashboard/list";
        return "user/login";

    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("user") RegistrationFormResponse formData,
            BindingResult bindingResult,
            Model model) {
        if (bindingResult.hasErrors()) {
            return "user/registration";
        }
        String confirmUrl = userService.registerNewUser(formData);
        model.addAttribute("confirmUrl", confirmUrl);
        return "user/confirmEmail";
    }

    @GetMapping("/confirm")
    public String confirm(@RequestParam String confirm) {
        if (Boolean.TRUE.equals(userService.confirmEmailString(confirm)))
            return "/user/login";
        return "/user/confirmError";
    }
}
