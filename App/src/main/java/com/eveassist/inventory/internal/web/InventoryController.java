package com.eveassist.inventory.internal.web;

import com.eveassist.identity.Viewer;
import com.eveassist.inventory.internal.AssetFetcher;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * The inventory page. Opening it queues a fetch for each character, which ESI's cache times make free while its copy is
 * current, and shows what is stored at once. The status block asks for news every two seconds while a fetch runs.
 */
@Controller
class InventoryController {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final AssetFetcher fetcher;

    InventoryController(AssetFetcher fetcher) {
        this.fetcher = fetcher;
    }

    /** A character's row on the page: the fetch phase and when, in UTC (the account's time zone comes later). */
    public record Row(java.util.UUID id, String name, String phase, String fetchedAt, Integer itemCount,
            String problem) {
    }

    @GetMapping("/inventory")
    String page(Viewer viewer, Model model) {
        fetcher.refresh(viewer);
        fill(viewer, model);
        return "inventory/list";
    }

    @GetMapping("/inventory/status")
    String status(Viewer viewer, Model model) {
        fill(viewer, model);
        return "inventory/fragments :: status";
    }

    @PostMapping("/inventory/refresh")
    String refresh(Viewer viewer) {
        fetcher.refresh(viewer);
        return "redirect:/inventory";
    }

    private void fill(Viewer viewer, Model model) {
        model.addAttribute("rows", fetcher.status(viewer).stream()
                .map(status -> new Row(status.characterId(), status.name(), status.phase().name(),
                        status.fetchedAt() == null ? null : UTC.format(status.fetchedAt()), status.itemCount(),
                        status.problem()))
                .toList());
        model.addAttribute("running", fetcher.anyRunning(viewer));
    }
}
