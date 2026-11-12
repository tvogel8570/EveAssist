package com.eveassist.characters;

import com.eveassist.identity.Viewer;
import java.util.List;

/** What other modules may ask the {@code characters} module. Tokens never leave it. */
public interface LinkedCharacters {

    /** The viewer's linked characters, by name. Never another account's. */
    List<LinkedCharacter> of(Viewer viewer);

    /**
     * Reads from ESI which corporation each of the viewer's characters is in and whether it is a Director, for the
     * characters that granted the roles permission and whose answer is no longer fresh. It asks ESI, so call it from a
     * background task, never from a page. The results show in {@link LinkedCharacter#eveCorporationId()} and
     * {@link LinkedCharacter#director()}.
     */
    void learnCorporations(Viewer viewer);

    /** The features the viewer's account has turned on. A module reads data only for a feature that is on. */
    java.util.Set<Feature> featuresOn(Viewer viewer);
}
