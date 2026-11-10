package com.eveassist.inventory.internal.web;

import com.eveassist.identity.Viewer;
import com.eveassist.inventory.internal.AssetFetcher;
import com.eveassist.inventory.internal.BrowseFilter;
import com.eveassist.inventory.internal.BrowseFilter.Dimension;
import com.eveassist.inventory.internal.BrowseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * The inventory page. Opening it queues a fetch for each character, which ESI's cache times make free while its copy is
 * current, and shows what is stored at once. The filter is the address: every change asks for {@code /inventory} with
 * htmx, gets only the list back, and pushes the address, so a filtered view can be reloaded, bookmarked and sent. The
 * status block asks for news every two seconds while a fetch runs, and the list asks again when the fetch is done.
 */
@Controller
class InventoryController {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);
    /** The column whose list is open, or the minimum, is not sent as hidden fields: its own inputs send it. */
    private static final String MINIMUM = "min";

    private final AssetFetcher fetcher;
    private final BrowseService browse;
    private final MessageSource messages;

    InventoryController(AssetFetcher fetcher, BrowseService browse, MessageSource messages) {
        this.fetcher = fetcher;
        this.browse = browse;
        this.messages = messages;
    }

    /** A character's row in the status block: the fetch phase and when, in UTC (the account's time zone comes later). */
    public record Row(java.util.UUID id, String name, String phase, String fetchedAt, Integer itemCount,
            String problem, int structures, int structuresNamed) {
    }

    @GetMapping("/inventory")
    String page(Viewer viewer, HttpServletRequest request, HttpServletResponse response, Model model,
            @RequestHeader(value = "HX-Request", required = false) String htmx) {
        // the same address answers the whole page and, to htmx, only the list, so the browser must not mix them up
        response.addHeader("Vary", "HX-Request");
        var filter = BrowseFilter.parse(request.getParameterMap());
        var partial = htmx != null;
        var open = partial ? openList(request) : null;
        if (!partial) {
            fetcher.refresh(viewer);
            fillStatus(viewer, model);
        }
        fillBrowse(viewer, filter, open, model);
        model.addAttribute("keepOpen", open != null);
        return partial ? "inventory/browse :: partial" : "inventory/list";
    }

    /** The list asked for by the form's open column, or null when none is open or the form says it is closed. */
    private static String openList(HttpServletRequest request) {
        var values = request.getParameterValues("open");
        if (values == null || values.length == 0) {
            return null;
        }
        for (var value : values) {
            if (value.isEmpty()) {
                return null;
            }
        }
        return values[0];
    }

    private void fillBrowse(Viewer viewer, BrowseFilter filter, String open, Model model) {
        model.addAttribute("view", browse.browse(viewer, filter));
        model.addAttribute("filter", filter);
        model.addAttribute("search", filter.search());
        model.addAttribute("sort", filter.sort().name().toLowerCase(java.util.Locale.ROOT));
        model.addAttribute("hidden", hiddenFields(filter, open));
        var locale = LocaleContextHolder.getLocale();
        var columns = new ArrayList<String[]>();
        for (var dimension : Dimension.values()) {
            columns.add(new String[] {dimension.parameter,
                    messages.getMessage("inventory.filter." + dimension.parameter, null, locale)});
        }
        columns.add(new String[] {MINIMUM, messages.getMessage("inventory.filter.min", null, locale)});
        model.addAttribute("columns", columns);
    }

    /** The filter as hidden form fields, except for the part a list that is open already holds in its own inputs. */
    private static List<String[]> hiddenFields(BrowseFilter filter, String open) {
        var fields = new ArrayList<String[]>();
        for (var dimension : Dimension.values()) {
            if (dimension.parameter.equals(open)) {
                continue;
            }
            filter.valuesOf(dimension).forEach(value -> fields.add(new String[] {dimension.parameter, value}));
        }
        if (filter.minTotal() > 0 && !MINIMUM.equals(open)) {
            fields.add(new String[] {MINIMUM, String.valueOf(filter.minTotal())});
        }
        return fields;
    }

    /** The values of one column, as a list to tick. Opened from Add filter and from a chip. */
    @GetMapping("/inventory/filter/{column}")
    String filterList(Viewer viewer, @PathVariable String column, HttpServletRequest request, Model model,
            @RequestParam(name = "fq", required = false) String listSearch) {
        var filter = BrowseFilter.parse(request.getParameterMap());
        if (MINIMUM.equals(column)) {
            model.addAttribute("hidden", hiddenFields(filter, MINIMUM));
            model.addAttribute("minimum", filter.minTotal() > 0 ? String.valueOf(filter.minTotal()) : "");
            return "inventory/filter :: minimum";
        }
        var dimension = Dimension.of(column);
        if (dimension == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("facet", browse.facet(viewer, dimension, filter, listSearch));
        model.addAttribute("listSearch", listSearch == null ? "" : listSearch);
        model.addAttribute("hidden", hiddenFields(filter, dimension.parameter));
        model.addAttribute("preset", dimension == Dimension.HOW ? filter.looseOnly().toQuery() : "");
        model.addAttribute("clear", filter.without(dimension).toQuery());
        return "inventory/filter :: facet";
    }

    /** Where one item type is, for the row that was opened. */
    @GetMapping("/inventory/item/{typeId}")
    String where(Viewer viewer, @PathVariable int typeId, HttpServletRequest request, Model model) {
        var filter = BrowseFilter.parse(request.getParameterMap());
        model.addAttribute("lines", browse.where(viewer, filter, typeId));
        model.addAttribute("showCharacters", filter.showCharacters());
        return "inventory/browse :: where";
    }

    @GetMapping("/inventory/status")
    String status(Viewer viewer, Model model, HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String htmx) {
        fillStatus(viewer, model);
        if (htmx != null && !Boolean.TRUE.equals(model.getAttribute("running"))) {
            // the last answer of a poll: tell the list, once, that what it shows may have changed
            response.setHeader("HX-Trigger", "browse-refresh");
        }
        return "inventory/fragments :: status";
    }

    @PostMapping("/inventory/refresh")
    String refresh(Viewer viewer) {
        fetcher.refresh(viewer);
        return "redirect:/inventory";
    }

    /** Everything the filter lets through, one line for each item, character, place and way of holding. */
    @GetMapping("/inventory/export.csv")
    void export(Viewer viewer, HttpServletRequest request, HttpServletResponse response) throws IOException {
        var filter = BrowseFilter.parse(request.getParameterMap());
        var locale = LocaleContextHolder.getLocale();
        response.setContentType("text/csv");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=\"inventory.csv\"");
        response.setHeader("Cache-Control", "no-store");
        var out = response.getWriter();
        var header = new String[] {"inventory.csv.item", "inventory.csv.category", "inventory.csv.group",
                "inventory.csv.character", "inventory.csv.place", "inventory.csv.region", "inventory.csv.how",
                "inventory.csv.quantity", "inventory.csv.blueprint"};
        out.write(csvLine(java.util.Arrays.stream(header).map(key -> messages.getMessage(key, null, key, locale))
                .toArray(String[]::new)));
        for (var line : browse.export(viewer, filter)) {
            out.write(csvLine(line));
        }
    }

    /**
     * One CSV line. A cell that starts with {@code =}, {@code +}, {@code -} or {@code @} would be run as a formula by a
     * spreadsheet, so it gets a quote in front. Names come from the game, so a hostile name is possible.
     */
    static String csvLine(String... cells) {
        var line = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            var cell = cells[i] == null ? "" : cells[i];
            if (!cell.isEmpty() && "=+-@\t\r".indexOf(cell.charAt(0)) >= 0 && !cell.matches("-?\\d+")) {
                cell = "'" + cell;
            }
            if (i > 0) {
                line.append(',');
            }
            line.append('"').append(cell.replace("\"", "\"\"")).append('"');
        }
        return line.append("\r\n").toString();
    }

    private void fillStatus(Viewer viewer, Model model) {
        var statuses = fetcher.status(viewer);
        model.addAttribute("rows", statuses.stream()
                .map(status -> new Row(status.characterId(), status.name(), status.phase().name(),
                        status.fetchedAt() == null ? null : UTC.format(status.fetchedAt()), status.itemCount(),
                        status.problem(), status.structures(), status.structuresNamed()))
                .toList());
        var counts = new java.util.EnumMap<AssetFetcher.Phase, Integer>(AssetFetcher.Phase.class);
        statuses.forEach(status -> counts.merge(status.phase(), 1, Integer::sum));
        model.addAttribute("current", counts.getOrDefault(AssetFetcher.Phase.CURRENT, 0));
        model.addAttribute("waiting", counts.getOrDefault(AssetFetcher.Phase.RUNNING, 0)
                + counts.getOrDefault(AssetFetcher.Phase.NEVER, 0));
        model.addAttribute("failed", counts.getOrDefault(AssetFetcher.Phase.FAILED, 0));
        model.addAttribute("needAttention", counts.getOrDefault(AssetFetcher.Phase.NEEDS_REAUTHORIZATION, 0)
                + counts.getOrDefault(AssetFetcher.Phase.MISSING_SCOPE, 0));
        model.addAttribute("running", fetcher.anyRunning(viewer));
    }
}
