package com.eveassist.industry.internal.web;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.FacilityId;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.application.CostingOutOfDateException;
import com.eveassist.industry.internal.application.InvalidQuoteException;
import com.eveassist.industry.internal.application.IssueQuoteCommand;
import com.eveassist.industry.internal.application.NewVersionCommand;
import com.eveassist.industry.internal.application.NotQuotableException;
import com.eveassist.industry.internal.application.NothingToCostException;
import com.eveassist.industry.internal.application.ProjectChangedException;
import com.eveassist.industry.internal.application.ProjectDetails;
import com.eveassist.industry.internal.application.ProjectEditing;
import com.eveassist.industry.internal.application.ProjectEntry;
import com.eveassist.industry.internal.application.ProjectNotFoundException;
import com.eveassist.industry.internal.application.ProjectNotQuotedException;
import com.eveassist.industry.internal.application.ProjectQueries;
import com.eveassist.industry.internal.application.ProjectQuoting;
import com.eveassist.industry.internal.application.QuoteNotFoundException;
import com.eveassist.industry.internal.application.ShareableQuote;
import com.eveassist.industry.internal.application.QuoteExport;
import com.eveassist.industry.internal.application.SetProjectConfigCommand;
import com.eveassist.industry.internal.application.SetProjectItemCommand;
import com.eveassist.industry.internal.domain.BlueprintSource;
import com.eveassist.industry.internal.domain.ProjectConfig;
import com.eveassist.industry.internal.domain.ProjectItem;
import com.eveassist.industry.internal.domain.ProjectLockedException;
import com.eveassist.sde.TypeId;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
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
 * One project's page: the settings, the items, the delivered cost and the quote card (layout C of the mockup, agreed on 2026-11-17 and
 * 2026-11-25). Every change is a plain form post that redirects back to the page, so a reload never repeats it. Changing an input makes
 * the costing out of date, and the page asks for Cost it again before a quote can be issued (decided on 2026-11-25).
 */
@Controller
@RequestMapping("/projects/{id}")
@PreAuthorize("hasRole('USER')")
class ProjectPageController {

    private static final Pattern PROJECT_ADDRESS = Pattern.compile("/projects/([0-9a-fA-F-]{36})");
    private static final String SKILL_PREFIX = "skill-";

    private final ProjectQueries queries;
    private final ProjectEditing editing;
    private final ProjectEntry entry;
    private final ProjectQuoting quoting;
    private final QuoteExport quoteExport;
    private final ProjectStates states;
    private final Clock clock;

    ProjectPageController(ProjectQueries queries, ProjectEditing editing, ProjectEntry entry, ProjectQuoting quoting,
            QuoteExport quoteExport, ProjectStates states, Clock clock) {
        this.queries = queries;
        this.editing = editing;
        this.entry = entry;
        this.quoting = quoting;
        this.quoteExport = quoteExport;
        this.states = states;
        this.clock = clock;
    }

    @GetMapping
    String page(Viewer viewer, @PathVariable UUID id, Model model) {
        var project = queries.details(viewer, new ProjectId(id));
        var stored = project.costing() == null ? null : project.costing().costing();
        var document = stored == null ? null : stored.document();
        var latestQuote = project.quotes().isEmpty() ? null : project.quotes().getLast();
        var locked = project.status() == com.eveassist.industry.internal.domain.ProjectStatus.QUOTED;
        var state = states.of(project.status(), stored != null, latestQuote == null ? null : latestQuote.expiresAt());
        var current = project.costing() != null && project.costing().current();

        // names of the things the costing mentions by type: the items and products, for the warnings and the jobs
        Map<Integer, String> names = new HashMap<>();
        project.items().forEach(item -> names.put(item.type(), item.name()));
        project.products().forEach(product -> names.put(product.type(), product.name()));

        // sections the user has to see open, whatever they closed before: a warning that blocks the quote, and where the last error may be
        var failed = model.containsAttribute("error");
        model.addAttribute("openWarnings", stored != null && !stored.quotable());
        model.addAttribute("openSettings", failed);
        model.addAttribute("openItems", failed);
        // what each issued quote says to its buyer, to copy or to download (made from the quote as it was frozen)
        Map<Integer, ShareableQuote> exports = new LinkedHashMap<>();
        project.quotes().forEach(quote -> exports.put(quote.number(), quoteExport.of(viewer, new ProjectId(id), quote.number())));
        model.addAttribute("exports", exports);
        model.addAttribute("project", project);
        model.addAttribute("state", state);
        model.addAttribute("locked", locked);
        model.addAttribute("stored", stored);
        model.addAttribute("doc", document);
        model.addAttribute("reasons", project.costing() == null ? java.util.List.of() : project.costing().outOfDate());
        model.addAttribute("current", current);
        model.addAttribute("canIssue", !locked && current && stored != null && stored.quotable() && document.quote() != null);
        model.addAttribute("latestQuote", latestQuote);
        model.addAttribute("names", names);
        model.addAttribute("units", project.products().stream().mapToLong(ProjectDetails.ProductLine::quantity).sum());
        model.addAttribute("defaultExpiry", clock.instant().plus(Duration.ofDays(7)).atZone(ZoneOffset.UTC).toLocalDate());
        return "projects/page";
    }

    @PostMapping("/cost")
    String cost(Viewer viewer, @PathVariable UUID id, @RequestParam(required = false) String anchor, RedirectAttributes flash) {
        var run = entry.cost(viewer, new ProjectId(id));
        if (run.adjustedPricesProblem() != null) {
            flash.addFlashAttribute("pricesProblem", true);
        }
        return back(id, anchor);
    }

    @PostMapping("/config")
    String config(Viewer viewer, @PathVariable UUID id, @ModelAttribute ConfigForm form,
            @RequestParam(required = false) String anchor) {
        var project = new ProjectId(id);
        var current = queries.details(viewer, project).config();
        ProjectConfig changed;
        try {
            changed = form.applyTo(current);
        } catch (IllegalArgumentException invalid) {
            throw new InvalidFormException(invalid);
        }
        editing.setConfig(viewer, new SetProjectConfigCommand(project, changed, form.expectedVersion()));
        return back(id, anchor);
    }

    @PostMapping("/items/{type}")
    String item(Viewer viewer, @PathVariable UUID id, @PathVariable int type, @RequestParam long expectedVersion,
            @RequestParam int me, @RequestParam int te, @RequestParam(required = false) String facility,
            @RequestParam(required = false) BigDecimal blueprintCostPerRun, @RequestParam(required = false) String anchor,
            @RequestParam Map<String, String> all) {
        var project = new ProjectId(id);
        var line = queries.details(viewer, project).items().stream().filter(item -> item.type() == type).findFirst()
                .orElseThrow(ProjectNotFoundException::new);
        var skills = new HashMap<TypeId, Integer>();
        for (var skill : line.skills()) {
            var typed = all.get(SKILL_PREFIX + skill.skill());
            skills.put(new TypeId(skill.skill()), typed == null || typed.isBlank() ? skill.level() : Integer.parseInt(typed.strip()));
        }
        ProjectItem item;
        try {
            item = new ProjectItem(new TypeId(type), BlueprintSource.valueOf(line.blueprintSource().toUpperCase(java.util.Locale.ROOT)),
                    me, te, blueprintCostPerRun == null ? line.blueprintCostPerRun() : blueprintCostPerRun, facilityOf(facility),
                    line.policy(), skills);
        } catch (IllegalArgumentException invalid) {
            throw new InvalidFormException(invalid);
        }
        editing.setItem(viewer, new SetProjectItemCommand(project, item, expectedVersion));
        return back(id, anchor);
    }

    @PostMapping("/quotes")
    String issue(Viewer viewer, @PathVariable UUID id, @RequestParam long expectedVersion,
            @RequestParam(required = false) String buyer, @RequestParam(required = false) String note,
            @RequestParam(required = false) String expires, @RequestParam(required = false) String anchor) {
        var expiresAt = expires == null || expires.isBlank() ? null
                : LocalDate.parse(expires.strip()).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        quoting.issue(viewer, new IssueQuoteCommand(new ProjectId(id), expectedVersion, buyer, note, expiresAt));
        return back(id, anchor);
    }

    @PostMapping("/quotes/{number}/new-version")
    String newVersion(Viewer viewer, @PathVariable UUID id, @PathVariable int number, @RequestParam long expectedVersion,
            RedirectAttributes flash) {
        var made = quoting.newVersionFrom(viewer, new NewVersionCommand(new ProjectId(id), number, expectedVersion));
        if (!made.changedFacilities().isEmpty() || !made.goneFacilities().isEmpty()) {
            flash.addFlashAttribute("facilitiesChanged", true);
        }
        return back(id);
    }

    /** The quote as a CSV file, for a buyer who wants a sheet. It holds the products, the total, the buyer, the note and the expiry, and nothing of the cost. */
    @GetMapping("/quotes/{number}/csv")
    ResponseEntity<String> csv(Viewer viewer, @PathVariable UUID id, @PathVariable int number) {
        var shareable = quoteExport.of(viewer, new ProjectId(id), number);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(shareable.fileName()).build().toString())
                .body(shareable.csv());
    }

    @PostMapping("/quotes/{number}/delete")
    String deleteQuote(Viewer viewer, @PathVariable UUID id, @PathVariable int number) {
        quoting.deleteQuote(viewer, new ProjectId(id), number);
        return back(id);
    }

    private static String back(UUID id) {
        return "redirect:/projects/" + id;
    }

    /** An id the page has: letters, digits and dashes only, which a fragment of our own address cannot misuse. Anything else is left out. */
    private static final Pattern ANCHOR = Pattern.compile("[A-Za-z][A-Za-z0-9-]{0,39}");

    /** Back to the page, at the part of it the change was made in, so a long page does not jump to the top after a save. */
    private static String back(UUID id, String anchor) {
        return anchor != null && ANCHOR.matcher(anchor).matches() ? back(id) + "#" + anchor : back(id);
    }

    private static FacilityId facilityOf(String text) {
        return text == null || text.isBlank() ? null : new FacilityId(UUID.fromString(text.strip()));
    }

    // ---- what goes wrong, told on the page it happened on (a form post has nothing else to show it with) ----

    @ExceptionHandler({ProjectNotFoundException.class, QuoteNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound() {
        return "projects/not-found";
    }

    @ExceptionHandler({ProjectChangedException.class, CostingOutOfDateException.class, NotQuotableException.class,
            ProjectLockedException.class, ProjectNotQuotedException.class, InvalidQuoteException.class,
            NothingToCostException.class, InvalidFormException.class, MethodArgumentTypeMismatchException.class, org.springframework.validation.BindException.class,
            java.time.format.DateTimeParseException.class, NumberFormatException.class})
    String refused(Exception problem, HttpServletRequest request, RedirectAttributes flash) {
        var found = PROJECT_ADDRESS.matcher(request.getRequestURI());
        if (!found.find()) {
            throw new IllegalStateException("A refused change was not on a project's address: " + request.getRequestURI(), problem);
        }
        flash.addFlashAttribute("error", errorKey(problem));
        return "redirect:/projects/" + found.group(1);
    }

    private static String errorKey(Exception problem) {
        return switch (problem) {
            case ProjectChangedException changed -> "changed";
            case CostingOutOfDateException outOfDate -> "outOfDate";
            case NotQuotableException notQuotable -> "notQuotable";
            case ProjectLockedException locked -> "locked";
            case ProjectNotQuotedException notQuoted -> "notQuoted";
            case InvalidQuoteException invalid -> "invalidQuote";
            case NothingToCostException nothing -> "nothingToCost";
            default -> "invalid";
        };
    }

    /** What a form sent that the domain refuses: a margin of 100, an ME of 11. The page says "check the values". */
    static final class InvalidFormException extends RuntimeException {
        InvalidFormException(Throwable cause) {
            super(cause);
        }
    }
}
