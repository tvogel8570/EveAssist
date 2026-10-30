package com.eveassist.spike.industry.internal.web;

import com.eveassist.spike.identity.Viewer;
import com.eveassist.spike.industry.PlanId;
import com.eveassist.spike.industry.internal.application.ChangeLineQuantity;
import com.eveassist.spike.industry.internal.application.ChangeLineQuantityCommand;
import com.eveassist.spike.industry.internal.application.PlanQueries;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
class PlanPageController {
    private final PlanQueries queries;
    private final ChangeLineQuantity changeLineQuantity;

    PlanPageController(PlanQueries queries, ChangeLineQuantity changeLineQuantity) {
        this.queries = queries;
        this.changeLineQuantity = changeLineQuantity;
    }

    @GetMapping("/plans")
    String list(Viewer viewer, Model model) {
        model.addAttribute("rows", queries.listRows(viewer));
        return "plans/list";
    }

    @GetMapping("/plans/{id}")
    String editor(Viewer viewer, @PathVariable UUID id, Model model) {
        model.addAttribute("editor", queries.editor(viewer, new PlanId(id)));
        return "plans/editor";
    }

    @PostMapping("/plans/{id}/lines/{lineId}/quantity")
    String changeQuantity(Viewer viewer, @PathVariable UUID id, @PathVariable UUID lineId, QuantityForm form,
                          Model model) {
        var command = new ChangeLineQuantityCommand(new PlanId(id), lineId, form.quantity());
        model.addAttribute("change", changeLineQuantity.handle(viewer, command));
        return "plans/fragments :: changed";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    @ResponseBody
    String rejected(IllegalArgumentException e) {
        return e.getMessage();
    }
}
