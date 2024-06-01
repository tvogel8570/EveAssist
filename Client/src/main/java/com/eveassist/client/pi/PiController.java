package com.eveassist.client.pi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Slf4j
@Controller
@RequestMapping("/pi")
public class PiController {
    @GetMapping
    public String index() {
        return "pi/list";
    }

    @GetMapping("/refresh")
    public ModelAndView refresh(ModelMap model) {
        return new ModelAndView("pi/pi-refresh", model);
    }
}
