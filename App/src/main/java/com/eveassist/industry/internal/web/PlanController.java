package com.eveassist.industry.internal.web;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.PlanId;
import com.eveassist.industry.internal.application.AddProductCommand;
import com.eveassist.industry.internal.application.ChangeQuantityCommand;
import com.eveassist.industry.internal.application.ChoosePartSourceCommand;
import com.eveassist.industry.internal.application.CreatePlanCommand;
import com.eveassist.industry.internal.application.InvalidPlanNameException;
import com.eveassist.industry.internal.application.PlanChangedException;
import com.eveassist.industry.internal.application.PlanEditing;
import com.eveassist.industry.internal.application.PlanEntry;
import com.eveassist.industry.internal.application.PlanNotFoundException;
import com.eveassist.industry.internal.application.PlanQueries;
import com.eveassist.industry.internal.application.UnknownProductException;
import com.eveassist.industry.internal.domain.InvalidQuantityException;
import com.eveassist.sde.TypeId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
@RequestMapping("/plans")
@PreAuthorize("hasRole('USER')")
class PlanController {

    private final PlanQueries queries;
    private final PlanEditing editing;
    private final PlanEntry entry;

    PlanController(PlanQueries queries, PlanEditing editing, PlanEntry entry) {
        this.queries = queries;
        this.editing = editing;
        this.entry = entry;
    }

    @GetMapping
    String list(Viewer viewer, Model model) {
        model.addAttribute("plans", queries.overviews(viewer));
        return "plans/list";
    }

    @PostMapping
    String create(Viewer viewer, @RequestParam String name) {
        var id = editing.create(viewer, new CreatePlanCommand(name));
        return "redirect:/plans/" + id.value();
    }

    @GetMapping("/{id}")
    String editor(Viewer viewer, @PathVariable UUID id, Model model) {
        model.addAttribute("plan", entry.open(viewer, new PlanId(id)));
        return "plans/editor";
    }

    @PostMapping("/{id}/products")
    String addProduct(Viewer viewer, @PathVariable UUID id, @RequestParam int typeId) {
        entry.addProduct(viewer, new AddProductCommand(new PlanId(id), new TypeId(typeId), 1));
        return "redirect:/plans/" + id;
    }

    @PostMapping("/{id}/products/{typeId}/quantity")
    String changeQuantity(Viewer viewer, @PathVariable UUID id, @PathVariable int typeId,
            @RequestParam long quantity, @RequestParam long expectedVersion, Model model) {
        model.addAttribute("change", editing.changeQuantity(viewer,
                new ChangeQuantityCommand(new PlanId(id), new TypeId(typeId), quantity, expectedVersion)));
        model.addAttribute("editedTypeId", typeId);
        return "plans/fragments :: change";
    }

    @PostMapping("/{id}/parts/{typeId}/{source:buy|build}")
    String choosePartSource(Viewer viewer, @PathVariable UUID id, @PathVariable int typeId,
            @PathVariable String source, @RequestParam long expectedVersion, Model model) {
        model.addAttribute("plan", editing.choosePartSource(viewer, new ChoosePartSourceCommand(
                new PlanId(id), new TypeId(typeId), source.equals("buy"), expectedVersion)));
        return "plans/fragments :: planRedraw";
    }

    @PostMapping("/{id}/prices/refresh")
    String refreshPrices(Viewer viewer, @PathVariable UUID id, Model model) {
        var refresh = entry.refreshPrices(viewer, new PlanId(id));
        model.addAttribute("plan", refresh.plan());
        model.addAttribute("failedItems", refresh.failedItems());
        return "plans/fragments :: planRedraw";
    }

    @GetMapping("/{id}/product-search")
    String search(Viewer viewer, @PathVariable UUID id, @RequestParam(name = "q", defaultValue = "") String text,
            Model model) {
        model.addAttribute("planId", id);
        model.addAttribute("items", queries.searchProducts(viewer, text));
        model.addAttribute("searched", !text.isBlank());
        return "plans/fragments :: searchResults";
    }

    @ExceptionHandler(PlanNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound() {
        return "plans/not-found";
    }

    @ExceptionHandler(PlanChangedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    String changed() {
        return "plans/fragments :: empty";
    }

    @ExceptionHandler({InvalidQuantityException.class, UnknownProductException.class, InvalidPlanNameException.class})
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    String invalid() {
        return "plans/fragments :: empty";
    }
}
