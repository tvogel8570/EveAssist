package com.eveassist.industry.internal.web;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.FacilityId;
import com.eveassist.industry.internal.application.FacilityEditing;
import com.eveassist.industry.internal.application.FacilityNotFoundException;
import com.eveassist.industry.internal.application.FacilityQueries;
import com.eveassist.industry.internal.application.InvalidFacilityNameException;
import com.eveassist.industry.internal.domain.StructureType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The account's facilities: the page that lists them, and adding, changing and deleting one. The same forms are on a project's page (its
 * Facilities section), which sends {@code project} so that the user comes back to it. A facility belongs to the account and not to a project.
 */
@Controller
@RequestMapping("/facilities")
@PreAuthorize("hasRole('USER')")
class FacilityController {

    private final FacilityQueries queries;
    private final FacilityEditing editing;

    FacilityController(FacilityQueries queries, FacilityEditing editing) {
        this.queries = queries;
        this.editing = editing;
    }

    @GetMapping
    String list(Viewer viewer, Model model) {
        model.addAttribute("facilities", queries.list(viewer));
        model.addAttribute("structureTypes", List.of(StructureType.values()));
        return "facilities/list";
    }

    @PostMapping
    String add(Viewer viewer, @ModelAttribute FacilityForm form, @RequestParam(required = false) String project) {
        editing.add(viewer, form.values());
        return back(project);
    }

    @PostMapping("/{id}")
    String change(Viewer viewer, @PathVariable UUID id, @ModelAttribute FacilityForm form, @RequestParam(required = false) String project) {
        editing.change(viewer, new FacilityId(id), form.values());
        return back(project);
    }

    @PostMapping("/{id}/delete")
    String delete(Viewer viewer, @PathVariable UUID id, @RequestParam(required = false) String project, RedirectAttributes flash) {
        var deleted = editing.delete(viewer, new FacilityId(id));
        flash.addFlashAttribute("deletedDrafts", deleted.draftProjectsChanged());
        return back(project);
    }

    /** The facilities page, or the project the form was on (its Facilities section) when it says which. Anything but a project id is ignored. */
    private static String back(String project) {
        try {
            return project == null || project.isBlank() ? "redirect:/facilities"
                    : "redirect:/projects/" + UUID.fromString(project.strip()) + "#facilities";
        } catch (IllegalArgumentException notAnId) {
            return "redirect:/facilities";
        }
    }

    @ExceptionHandler(FacilityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound() {
        return "facilities/not-found";
    }

    /** A value the form sent that is refused: a name that is blank, a percent above 100, a number that is not one. Told on the page it came from. */
    @ExceptionHandler({InvalidFacilityNameException.class, IllegalArgumentException.class, MethodArgumentTypeMismatchException.class,
            org.springframework.validation.BindException.class})
    String refused(Exception problem, HttpServletRequest request, RedirectAttributes flash) {
        flash.addFlashAttribute("facilityError", problem instanceof InvalidFacilityNameException ? "name" : "invalid");
        return back(request.getParameter("project"));
    }
}
