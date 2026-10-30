package com.eveassist.spike.htmx;

import com.eveassist.spike.plan.PlanCosting;
import com.eveassist.spike.plan.PlanState;
import com.eveassist.spike.plan.PlanState.Decision;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The full page is rendered once. Every change posts to one of the endpoints below, which returns only the
 * {@code editor} fragment; htmx swaps it into the page.
 */
@Controller
public class PlanController {

    private final PlanState state;

    public PlanController(PlanState state) {
        this.state = state;
    }

    @GetMapping("/")
    public String page(Model model) {
        model.addAttribute("plan", PlanCosting.cost(state));
        return "plan";
    }

    @PostMapping("/plan/decision")
    public String decide(@RequestParam String path, @RequestParam Decision decision, Model model) {
        PlanCosting.decide(state, path, decision);
        return editor(model);
    }

    @PostMapping("/plan/facility")
    public String facility(@RequestParam String path, @RequestParam String facility, Model model) {
        state.setFacility(path, facility);
        return editor(model);
    }

    @PostMapping("/plan/quantity")
    public String quantity(@RequestParam int quantity, Model model) {
        state.setQuantity(quantity);
        return editor(model);
    }

    @PostMapping("/plan/reset")
    public String reset(Model model) {
        state.reset();
        return editor(model);
    }

    private String editor(Model model) {
        model.addAttribute("plan", PlanCosting.cost(state));
        return "plan :: editor";
    }
}
