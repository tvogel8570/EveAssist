package com.eveassist.industry.internal.web;

import com.eveassist.identity.Visitor;
import com.eveassist.industry.internal.application.TryPlans;
import com.eveassist.industry.internal.application.TryResult;
import com.eveassist.industry.internal.application.UnknownProductException;
import com.eveassist.industry.internal.domain.InvalidQuantityException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A plan for a visitor who has not signed in. It is open to everyone and stores nothing: the browser keeps the plan and
 * sends it back as {@code state} with every request. The routes need no CSRF token, because they change nothing that
 * belongs to anyone (see SecurityConfig).
 */
@Controller
@RequestMapping("/try")
class TryController {

    private final TryPlans plans;

    TryController(TryPlans plans) {
        this.plans = plans;
    }

    /** The empty page. The browser then sends the plan it kept, if any, to {@code /try/render}. */
    @GetMapping
    String page(HttpServletRequest request, Model model) {
        show(plans.open(visitor(request), ""), model);
        return "plans/try";
    }

    /** Draws the plan the browser kept. */
    @PostMapping("/render")
    String render(HttpServletRequest request, @RequestParam(defaultValue = "") String state, Model model) {
        show(plans.open(visitor(request), state), model);
        return "plans/fragments :: tryProductsRedraw";
    }

    @PostMapping("/products")
    String addProduct(HttpServletRequest request, @RequestParam(defaultValue = "") String state,
            @RequestParam int typeId, @RequestParam(defaultValue = "1") long quantity, Model model) {
        show(plans.addProduct(visitor(request), state, typeId, quantity), model);
        return "plans/fragments :: tryProductsRedraw";
    }

    /** Puts a removed product back with the units it had, which is what Undo does. */
    @PostMapping("/products/{typeId}/restore")
    String restoreProduct(HttpServletRequest request, @RequestParam(defaultValue = "") String state,
            @PathVariable int typeId, @RequestParam long quantity, Model model) {
        show(plans.addProduct(visitor(request), state, typeId, quantity), model);
        return "plans/fragments :: tryProductsRedraw";
    }

    @PostMapping("/products/{typeId}/quantity")
    String changeQuantity(HttpServletRequest request, @RequestParam(defaultValue = "") String state,
            @PathVariable int typeId, @RequestParam long quantity, Model model) {
        show(plans.changeQuantity(visitor(request), state, typeId, quantity), model);
        return "plans/fragments :: tryPlanRedraw";
    }

    @PostMapping("/products/{typeId}/remove")
    String removeProduct(HttpServletRequest request, @RequestParam(defaultValue = "") String state,
            @PathVariable int typeId, Model model) {
        show(plans.removeProduct(visitor(request), state, typeId), model);
        return "plans/fragments :: tryProductsRedraw";
    }

    @PostMapping("/parts/{typeId}/{source:buy|build}")
    String choosePartSource(HttpServletRequest request, @RequestParam(defaultValue = "") String state,
            @PathVariable int typeId, @PathVariable String source, Model model) {
        show(plans.choosePartSource(visitor(request), state, typeId, source.equals("buy")), model);
        return "plans/fragments :: tryPlanRedraw";
    }

    @GetMapping("/product-search")
    String search(HttpServletRequest request, @RequestParam(name = "q", defaultValue = "") String text, Model model) {
        model.addAttribute("tryMode", true);
        model.addAttribute("items", plans.searchProducts(visitor(request), text));
        model.addAttribute("searched", !text.isBlank());
        return "plans/fragments :: searchResults";
    }

    private static void show(TryResult result, Model model) {
        model.addAttribute("tryMode", true);
        model.addAttribute("plan", result.plan());
        model.addAttribute("tryState", result.state());
        model.addAttribute("unpriced", result.unpriced());
        model.addAttribute("stale", result.stale());
        model.addAttribute("notice", result.notice());
        model.addAttribute("removed", result.removed());
        model.addAttribute("failedItems", java.util.List.of());
    }

    /** What limits one visitor is the address the request came from. */
    private static Visitor visitor(HttpServletRequest request) {
        return new Visitor(request.getRemoteAddr());
    }

    @ExceptionHandler({InvalidQuantityException.class, UnknownProductException.class})
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    String invalid() {
        return "plans/fragments :: empty";
    }
}
