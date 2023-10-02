package com.eveassist.client.pi;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/pi")
public class PiController {
    @GetMapping
    public String index(Model model) {
        return "pi/list";
    }
}
