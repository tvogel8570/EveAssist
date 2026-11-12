package com.eveassist.market.internal.web;

import com.eveassist.market.internal.ContractPrices;
import com.eveassist.sde.SdeService;
import com.eveassist.sde.TypeId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Prices of blueprint copies from public contracts. */
@Controller
@RequestMapping("/market/blueprint-copies")
@PreAuthorize("hasRole('USER')")
class BlueprintCopyController {

    /** Contracts whose lines one refresh reads. A region has many thousands, so a refresh reads a batch. */
    static final int CONTRACTS_PER_REFRESH = 500;

    private final ContractPrices prices;
    private final SdeService sde;

    BlueprintCopyController(ContractPrices prices, SdeService sde) {
        this.prices = prices;
        this.sde = sde;
    }

    @GetMapping
    String page(@RequestParam(required = false) String q, @RequestParam(required = false) Integer type,
                @RequestParam(defaultValue = "0") int me, @RequestParam(defaultValue = "0") int te, Model model) {
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("status", prices.status(ContractPrices.THE_FORGE));
        if (q != null && !q.isBlank()) {
            model.addAttribute("found", sde.searchBlueprints(q, 12));
        }
        if (type != null && sde.isBlueprint(new TypeId(type))) {
            var blueprint = new TypeId(type);
            var chosenMe = Math.clamp(me, 0, 10);
            var chosenTe = Math.clamp(te, 0, 20);
            model.addAttribute("blueprint", sde.itemType(blueprint).orElseThrow());
            model.addAttribute("me", chosenMe);
            model.addAttribute("te", chosenTe);
            model.addAttribute("quote", prices.bpcPrice(ContractPrices.THE_FORGE, blueprint, chosenMe, chosenTe).orElse(null));
        }
        return "market/blueprint-copies";
    }

    @PostMapping("/refresh")
    String refresh(@RequestParam(required = false) String q, @RequestParam(required = false) Integer type,
                   @RequestParam(defaultValue = "0") int me, @RequestParam(defaultValue = "0") int te,
                   RedirectAttributes redirect) {
        redirect.addFlashAttribute("refresh", prices.refresh(ContractPrices.THE_FORGE, CONTRACTS_PER_REFRESH));
        if (q != null) {
            redirect.addAttribute("q", q);
        }
        if (type != null) {
            redirect.addAttribute("type", type);
            redirect.addAttribute("me", me);
            redirect.addAttribute("te", te);
        }
        return "redirect:/market/blueprint-copies";
    }
}
