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
 * The account's facilities: the page that lists them, and adding, changing and deleting one. The Default facility dropdown of a project leads
 * here with {@code project}, and a facility added then returns the user to that project. A facility belongs to the account and not to a project.
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

    /** {@code project} is the project the user came from (the Default facility dropdown), to offer a way back and to return to after adding one. */
    @GetMapping
    String list(Viewer viewer, @RequestParam(required = false) String project, Model model) {
        model.addAttribute("project", projectId(project));
        model.addAttribute("facilities", queries.list(viewer));
        model.addAttribute("structureTypes", List.of(StructureType.values()));
        return "facilities/list";
    }

    /** A facility added for a project returns to that project's Settings, where it can be chosen. Added from the menu, it stays on this page. */
    @PostMapping
    String add(Viewer viewer, @ModelAttribute FacilityForm form, @RequestParam(required = false) String project) {
        editing.add(viewer, form.values());
        var id = projectId(project);
        return id == null ? "redirect:/facilities" : "redirect:/projects/" + id + "#settings";
    }

    @PostMapping("/{id}")
    String change(Viewer viewer, @PathVariable UUID id, @ModelAttribute FacilityForm form, @RequestParam(required = false) String project) {
        editing.change(viewer, new FacilityId(id), form.values());
        return staying(project);
    }

    @PostMapping("/{id}/delete")
    String delete(Viewer viewer, @PathVariable UUID id, @RequestParam(required = false) String project, RedirectAttributes flash) {
        var deleted = editing.delete(viewer, new FacilityId(id));
        flash.addFlashAttribute("deletedDrafts", deleted.draftProjectsChanged());
        return staying(project);
    }

    /** The project id the user came from, or null for nothing or for anything that is not one, so the address cannot be pointed anywhere else. */
    private static UUID projectId(String project) {
        try {
            return project == null || project.isBlank() ? null : UUID.fromString(project.strip());
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    /** This page again, still remembering the project the user came from. */
    private static String staying(String project) {
        var id = projectId(project);
        return id == null ? "redirect:/facilities" : "redirect:/facilities?project=" + id;
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
        return staying(request.getParameter("project"));
    }
}
