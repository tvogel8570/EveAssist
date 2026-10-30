package com.eveassist.spike.react;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The React app decides which page to show from the URL, so every page URL must return the same index.html. Without
 * this, opening /jobs directly would be a 404.
 */
@Controller
public class PageController {

    @GetMapping("/jobs")
    public String jobs() {
        return "forward:/index.html";
    }
}
