package com.eveassist.industry.internal.project.web;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.PlanId;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.project.application.InvalidProjectNameException;
import com.eveassist.industry.internal.plan.application.PlanNotFoundException;
import com.eveassist.industry.internal.plan.application.PlanQueries;
import com.eveassist.industry.internal.project.application.ProjectEditing;
import com.eveassist.industry.internal.project.application.ProjectEntry;
import com.eveassist.industry.internal.project.application.ProjectNotFoundException;
import com.eveassist.industry.internal.project.application.ProjectOverview;
import com.eveassist.industry.internal.project.application.ProjectQueries;
import com.eveassist.industry.internal.project.application.StartProjectCommand;
import java.util.List;
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

/** The Projects list, and starting, archiving and deleting a project. The project's own page is {@link ProjectPageController}. */
@Controller
@RequestMapping("/projects")
@PreAuthorize("hasRole('USER')")
class ProjectController {

    private final ProjectQueries queries;
    private final ProjectEditing editing;
    private final ProjectEntry entry;
    private final PlanQueries plans;
    private final ProjectStates states;

    ProjectController(ProjectQueries queries, ProjectEditing editing, ProjectEntry entry, PlanQueries plans,
            ProjectStates states) {
        this.queries = queries;
        this.editing = editing;
        this.entry = entry;
        this.plans = plans;
        this.states = states;
    }

    /**
     * The list. {@code view} is {@code table} or {@code cards}, {@code state} narrows it to one state (draft, costed, quoted or
     * expired), and {@code archived} adds the archived projects. All three are in the address, so a page can be shared and reloaded.
     */
    @GetMapping
    String list(Viewer viewer, @RequestParam(name = "view", defaultValue = "table") String view,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "archived", required = false) String archived, Model model) {
        var showArchived = archived != null;
        var wanted = state != null && ProjectStates.ALL.contains(state) ? state : null;
        var everything = queries.overviews(viewer, showArchived);
        List<ProjectOverview> shown = wanted == null ? everything
                : everything.stream().filter(project -> states.of(project).equals(wanted)).toList();
        model.addAttribute("projects", shown);
        model.addAttribute("view", view.equals("cards") ? "cards" : "table");
        model.addAttribute("state", wanted);
        model.addAttribute("showArchived", showArchived);
        model.addAttribute("states", ProjectStates.ALL);
        // "no projects yet" is only said when there are none at all, archived ones included
        model.addAttribute("anyProject", !everything.isEmpty() || !queries.overviews(viewer, true).isEmpty());
        model.addAttribute("plans", plans.overviews(viewer, false));
        return "projects/list";
    }

    /** Copies the plan into a new project and costs it, then opens the project. A blank name takes the plan's own. */
    @PostMapping
    String start(Viewer viewer, @RequestParam UUID plan, @RequestParam(required = false) String name) {
        // the command refuses a blank name, and the page's own rule is that a blank one takes the plan's
        var chosen = name == null || name.isBlank() ? null : name;
        var started = entry.startAndCost(viewer, new StartProjectCommand(new PlanId(plan), chosen));
        return "redirect:/projects/" + started.project().value();
    }

    @PostMapping("/{id}/archive")
    String archive(Viewer viewer, @PathVariable UUID id,
            @RequestParam(required = false) String view, @RequestParam(required = false) String archived) {
        editing.archive(viewer, new ProjectId(id));
        return "redirect:" + listAddress(view, archived);
    }

    @PostMapping("/{id}/restore")
    String restore(Viewer viewer, @PathVariable UUID id, @RequestParam(defaultValue = "list") String from,
            @RequestParam(required = false) String view, @RequestParam(required = false) String archived) {
        editing.restore(viewer, new ProjectId(id));
        return from.equals("project") ? "redirect:/projects/" + id : "redirect:" + listAddress(view, archived);
    }

    /** Deletes the project and its quotes for good. The page asks for a confirmation before it sends this. */
    @PostMapping("/{id}/delete")
    String delete(Viewer viewer, @PathVariable UUID id, @RequestParam(required = false) String view,
            @RequestParam(required = false) String archived) {
        editing.delete(viewer, new ProjectId(id));
        return "redirect:" + listAddress(view, archived);
    }

    /** The list as the user had it: the same layout, and with archived projects shown, when they were. */
    private static String listAddress(String view, String archived) {
        var address = new StringBuilder("/projects");
        var separator = '?';
        if ("cards".equals(view)) {
            address.append(separator).append("view=cards");
            separator = '&';
        }
        if (archived != null) {
            address.append(separator).append("archived=1");
        }
        return address.toString();
    }

    @ExceptionHandler({ProjectNotFoundException.class, PlanNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound() {
        return "projects/not-found";
    }

    @ExceptionHandler(InvalidProjectNameException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    String invalid() {
        return "projects/not-found";
    }
}
