package com.eveassist.spike.react;

import com.eveassist.spike.plan.PlanCosting;
import com.eveassist.spike.plan.PlanCosting.CostedPlan;
import com.eveassist.spike.plan.PlanState;
import com.eveassist.spike.plan.PlanState.Decision;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON API for the React app. Every change returns the whole recalculated plan. */
@RestController
@RequestMapping("/api/plan")
public class PlanApiController {

    public record DecisionChange(String path, Decision decision) {
    }

    public record FacilityChange(String path, String facility) {
    }

    public record QuantityChange(int quantity) {
    }

    private final PlanState state;

    public PlanApiController(PlanState state) {
        this.state = state;
    }

    @GetMapping
    public CostedPlan plan() {
        return PlanCosting.cost(state);
    }

    @PostMapping("/decision")
    public CostedPlan decide(@RequestBody DecisionChange change) {
        PlanCosting.decide(state, change.path(), change.decision());
        return plan();
    }

    @PostMapping("/facility")
    public CostedPlan facility(@RequestBody FacilityChange change) {
        state.setFacility(change.path(), change.facility());
        return plan();
    }

    @PostMapping("/quantity")
    public CostedPlan quantity(@RequestBody QuantityChange change) {
        state.setQuantity(change.quantity());
        return plan();
    }

    @PostMapping("/reset")
    public CostedPlan reset() {
        state.reset();
        return plan();
    }
}
