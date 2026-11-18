package com.eveassist.inventory.internal;

import com.eveassist.characters.Feature;
import com.eveassist.characters.LinkedCharacter;
import com.eveassist.characters.LinkedCharacters;
import com.eveassist.identity.Viewer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The colour of each holder of an account's items, the same on every page and in every bar. It comes from the holder's
 * place: the characters in the order they were first linked, so linking a character never changes the colour of
 * another, and then the corporations in the order the account lists them, when the account has the Corporation assets
 * feature on. A corporation's place is among all the account's corporations, not among those a page shows, so a page
 * that leaves some out still gives each of the others the colour it has everywhere else. More than {@link #COLORS}
 * holders share colours. The colours are classes in {@code app.css} ({@code seg-0} and on), so no style is written into
 * a page.
 */
@Component
class HolderColors {

    static final int COLORS = 24;

    private final LinkedCharacters linked;
    private final CorporationRepository corporations;

    HolderColors(LinkedCharacters linked, CorporationRepository corporations) {
        this.linked = linked;
        this.corporations = corporations;
    }

    /** The colour number of each character and corporation of the viewer's account, by the holder's id. */
    Map<UUID, Integer> of(Viewer viewer) {
        var characters = linked.of(viewer).stream()
                .sorted(Comparator.comparing(LinkedCharacter::linkedAt).thenComparingLong(LinkedCharacter::eveCharacterId))
                .toList();
        var colors = new HashMap<UUID, Integer>();
        for (var i = 0; i < characters.size(); i++) {
            colors.put(characters.get(i).id(), i % COLORS);
        }
        if (!characters.isEmpty() && linked.featuresOn(viewer).contains(Feature.CORPORATION_ASSETS)) {
            var own = corporations.listFor(viewer.accountId().value());
            for (var i = 0; i < own.size(); i++) {
                colors.put(own.get(i).id(), (characters.size() + i) % COLORS);
            }
        }
        return colors;
    }
}
