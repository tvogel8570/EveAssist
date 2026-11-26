package com.eveassist.industry.internal.web;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.internal.application.FacilityNotFoundException;
import com.eveassist.industry.internal.application.ProjectDefaults;
import com.eveassist.sde.TypeId;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The page of the account's project defaults, which a new project starts from. One form saves the whole page. */
@Controller
@RequestMapping("/project-defaults")
@PreAuthorize("hasRole('USER')")
class ProjectDefaultsController {

    private final ProjectDefaults defaults;

    ProjectDefaultsController(ProjectDefaults defaults) {
        this.defaults = defaults;
    }

    @GetMapping
    String page(Viewer viewer, Model model) {
        var view = defaults.view(viewer);
        model.addAttribute("view", view);
        model.addAttribute("maxJobDays", ConfigForm.days(view.config().defaultPolicy().maxJobSeconds()));
        return "projects/defaults";
    }

    @PostMapping
    String save(Viewer viewer, @ModelAttribute ConfigForm form, @RequestParam Map<String, String> all, RedirectAttributes flash) {
        var view = defaults.view(viewer);
        // only the skills the page shows are read
        var skills = new HashMap<TypeId, Integer>();
        view.skillGroups().forEach(group -> group.skills().forEach(skill -> {
            var typed = all.get(ProjectPageController.SKILL_PREFIX + skill.skill());
            if (typed != null && !typed.isBlank()) {
                skills.put(new TypeId(skill.skill()), Integer.parseInt(typed.strip()));
            }
        }));
        defaults.save(viewer, form.applyTo(view.config(), skills));
        flash.addFlashAttribute("saved", "saved");
        return "redirect:/project-defaults";
    }

    @PostMapping("/reset")
    String reset(Viewer viewer, RedirectAttributes flash) {
        defaults.reset(viewer);
        flash.addFlashAttribute("saved", "reset");
        return "redirect:/project-defaults";
    }

    /** A value that is refused: out of range, not a number, or a facility that is not there (any more). Nothing is saved. */
    @ExceptionHandler({IllegalArgumentException.class, FacilityNotFoundException.class, MethodArgumentTypeMismatchException.class,
            org.springframework.validation.BindException.class})
    String refused(Exception problem, RedirectAttributes flash) {
        flash.addFlashAttribute("error", problem instanceof FacilityNotFoundException ? "facility" : "invalid");
        return "redirect:/project-defaults";
    }
}
