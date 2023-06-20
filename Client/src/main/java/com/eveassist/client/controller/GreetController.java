package com.eveassist.client.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/")
public class GreetController {
    @GetMapping("/greet")
    @ResponseBody
    public String greet() {
        return "hello";
    }
}
