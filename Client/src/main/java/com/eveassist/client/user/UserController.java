package com.eveassist.client.user;

import com.eveassist.client.user.response.RegistrationFormResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/user")
public class UserController {
    private final EveAssistUserService userService;

    public UserController(EveAssistUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/create")
    public String createRegistrationForm(Model model) {
        model.addAttribute("user", new RegistrationFormResponse());
        return "/user/registration";
    }

    @GetMapping("/")
    public String loginRegister(Model model) {
        return "/user/login";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("user") RegistrationFormResponse formData,
                           BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return "user/registration";
        }
        String confirmUrl = userService.registerNewUser(formData);
        model.addAttribute("confirmUrl", confirmUrl);
        return "user/confirmEmail";
    }

    @GetMapping("/confirm")
    public String confirm(@RequestParam String confirm) {
        if (userService.confirmEmailString(confirm))
            return "/user/login";
        return "/user/confirmError";
    }
}
