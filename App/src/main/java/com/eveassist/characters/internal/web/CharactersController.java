package com.eveassist.characters.internal.web;

import com.eveassist.characters.Feature;
import com.eveassist.characters.internal.CharacterService;
import com.eveassist.identity.Viewer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/** The Characters page: features on top, a card per linked character below. */
@Controller
class CharactersController {

    /** The results the linking flow reports in {@code ?linking=}. Anything else in the address is ignored. */
    private static final Set<String> LINKING_RESULTS = Set.of("LINKED", "RENEWED", "ON_ANOTHER_ACCOUNT",
            "STATE_MISMATCH", "REFUSED", "declined", "no-feature");

    private final CharacterService characters;

    CharactersController(CharacterService characters) {
        this.characters = characters;
    }

    /** A feature as the page shows it. The scopes are listed on the page, so the user sees what is asked for. */
    record FeatureRow(Feature feature, boolean on, List<String> scopes) {
    }

    @GetMapping("/characters")
    String page(Viewer viewer, @RequestParam(required = false) String linking,
            @RequestParam(required = false) String removed, Model model) {
        var on = characters.featuresOn(viewer);
        model.addAttribute("features", Arrays.stream(Feature.values())
                .map(feature -> new FeatureRow(feature, on.contains(feature), feature.scopes().stream().sorted().toList()))
                .toList());
        model.addAttribute("characters", characters.list(viewer));
        model.addAttribute("anyFeatureOn", !on.isEmpty());
        model.addAttribute("linking", linking != null && LINKING_RESULTS.contains(linking) ? linking : null);
        model.addAttribute("removed", removed != null);
        return "characters/list";
    }

    /**
     * Turns a feature on or off. The toggle sends {@code on=true} only when it is checked, so a missing value means
     * off. The page reloads (htmx {@code HX-Refresh}) so every card shows what the change means for it.
     */
    @PostMapping("/characters/features/{feature}")
    ResponseEntity<Void> setFeature(Viewer viewer, @PathVariable Feature feature,
            @RequestParam(required = false) String on, @RequestHeader(value = "HX-Request", required = false) String htmx) {
        characters.setFeature(viewer, feature, "true".equals(on));
        if (htmx != null) {
            return ResponseEntity.noContent().header("HX-Refresh", "true").build();
        }
        return ResponseEntity.status(303).header("Location", "/characters").build();
    }

    @PostMapping("/characters/{id}/remove")
    String remove(Viewer viewer, @PathVariable UUID id) {
        return characters.remove(viewer, id) ? "redirect:/characters?removed" : "redirect:/characters";
    }

    @GetMapping("/characters/{id}/portrait")
    ResponseEntity<byte[]> portrait(Viewer viewer, @PathVariable UUID id) {
        return characters.portrait(viewer, id)
                .map(image -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                        .body(image))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
