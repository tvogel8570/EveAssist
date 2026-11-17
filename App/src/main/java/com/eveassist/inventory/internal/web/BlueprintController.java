package com.eveassist.inventory.internal.web;

import com.eveassist.identity.Viewer;
import com.eveassist.inventory.internal.BlueprintFetcher;
import com.eveassist.inventory.internal.BlueprintFilter;
import com.eveassist.inventory.internal.BlueprintService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** The searchable Blueprints table and its drawer detail. */
@Controller
@PreAuthorize("hasRole('USER')")
class BlueprintController {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneOffset.UTC);

    private final BlueprintFetcher fetcher;
    private final BlueprintService blueprints;
    private final MessageSource messages;

    BlueprintController(BlueprintFetcher fetcher, BlueprintService blueprints, MessageSource messages) {
        this.fetcher = fetcher;
        this.blueprints = blueprints;
        this.messages = messages;
    }

    @GetMapping("/blueprints")
    String page(Viewer viewer, HttpServletRequest request, HttpServletResponse response, Model model,
            @RequestHeader(value = "HX-Request", required = false) String htmxHeader,
            @RequestHeader(value = "HX-History-Restore-Request", required = false) String restore) {
        response.addHeader("Vary", "HX-Request, HX-History-Restore-Request");
        var htmx = partial(htmxHeader, restore);
        var filter = BlueprintFilter.parse(request.getParameterMap());
        fill(viewer, filter, model);
        if (htmx == null) {
            fillStatus(viewer, model);
            model.addAttribute("startOnLoad", true);
        }
        return htmx == null ? "blueprints/list" : "blueprints/results :: results";
    }

    @GetMapping("/blueprints/status")
    String status(Viewer viewer, Model model, HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String htmx,
            @RequestParam(name = "start", required = false) String start) {
        // only start=1 starts a read: the poll must never restart the read it is waiting for
        if ("1".equals(start)) {
            fetcher.refresh(viewer);
        }
        fillStatus(viewer, model);
        if (htmx != null && !Boolean.TRUE.equals(model.getAttribute("running"))) {
            response.setHeader("HX-Trigger", "blueprints-refresh");
        }
        return "blueprints/fragments :: status";
    }

    @PostMapping("/blueprints/refresh")
    String refresh(Viewer viewer) {
        fetcher.refresh(viewer);
        return "redirect:/blueprints";
    }

    @GetMapping("/blueprints/{itemId}")
    String detail(Viewer viewer, @PathVariable long itemId, HttpServletRequest request, HttpServletResponse response,
            Model model, @RequestHeader(value = "HX-Request", required = false) String htmxHeader,
            @RequestHeader(value = "HX-History-Restore-Request", required = false) String restore) {
        response.addHeader("Vary", "HX-Request, HX-History-Restore-Request");
        var htmx = partial(htmxHeader, restore);
        var filter = BlueprintFilter.parse(request.getParameterMap());
        var detail = blueprints.detail(viewer, itemId, filter)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("detail", detail);
        if (htmx != null) {
            return "blueprints/detail :: drawer";
        }
        fill(viewer, filter, model);
        model.addAttribute("detail", detail);
        model.addAttribute("drawerOpen", true);
        fillStatus(viewer, model);
        model.addAttribute("startOnLoad", true);
        return "blueprints/list";
    }

    @PostMapping("/blueprints/export.csv")
    void export(Viewer viewer, @RequestParam(name = "item", required = false) List<Long> itemIds,
            HttpServletResponse response) throws IOException {
        var visible = blueprints.selected(viewer, itemIds == null ? List.of() : itemIds);
        var locale = LocaleContextHolder.getLocale();
        response.setContentType("text/csv");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=\"blueprints.csv\"");
        response.setHeader("Cache-Control", "no-store");
        var out = response.getWriter();
        var header = new String[] {"blueprints.csv.type", "blueprints.csv.kind", "blueprints.csv.status",
                "blueprints.csv.me", "blueprints.csv.te", "blueprints.csv.runs", "blueprints.csv.holder",
                "blueprints.csv.place"};
        out.write(InventoryController.csvLine(java.util.Arrays.stream(header)
                .map(key -> messages.getMessage(key, null, key, locale)).toArray(String[]::new)));
        for (var item : visible) {
            out.write(InventoryController.csvLine(item.name(), item.kind(),
                    messages.getMessage("blueprints.status.notRead", null, locale),
                    Integer.toString(item.materialEfficiency()), Integer.toString(item.timeEfficiency()),
                    item.runs() < 0 ? "" : Integer.toString(item.runs()), item.holderName(), fullPlace(item)));
        }
    }

    /** htmx asks for a history entry with the same header as for a fragment, but wants the whole page back. */
    private static String partial(String htmx, String historyRestore) {
        return historyRestore != null ? null : htmx;
    }

    private void fill(Viewer viewer, BlueprintFilter filter, Model model) {
        model.addAttribute("view", blueprints.browse(viewer, filter));
        model.addAttribute("filter", filter);
        model.addAttribute("search", filter.search());
        model.addAttribute("sort", filter.sort());
        model.addAttribute("drawerOpen", false);
    }

    private void fillStatus(Viewer viewer, Model model) {
        var status = fetcher.status(viewer);
        model.addAttribute("statuses", status);
        model.addAttribute("summary", BlueprintFetcher.summarize(status));
        model.addAttribute("running", fetcher.anyRunning(viewer));
        model.addAttribute("utc", UTC);
    }

    public static String fullPlace(BlueprintService.Blueprint item) {
        var parts = new java.util.ArrayList<String>();
        parts.add(item.place());
        if (item.hangar() != null && !item.hangar().isBlank()) {
            parts.add(item.hangar());
        }
        if (item.container() != null && !item.container().isBlank()) {
            parts.add(item.container());
        }
        if (!item.matched()) {
            parts.add("not matched yet");
        }
        return String.join(" › ", parts);
    }
}