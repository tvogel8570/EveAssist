package com.eveassist.client.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
public class GreetController {
    @GetMapping("/greet")
    public String greet() {
        return "hello";
    }
}
