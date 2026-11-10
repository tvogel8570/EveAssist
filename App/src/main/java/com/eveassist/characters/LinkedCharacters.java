package com.eveassist.characters;

import com.eveassist.identity.Viewer;
import java.util.List;

/** What other modules may ask the {@code characters} module. Tokens never leave it. */
public interface LinkedCharacters {

    /** The viewer's linked characters, by name. Never another account's. */
    List<LinkedCharacter> of(Viewer viewer);

    /** The features the viewer's account has turned on. A module reads data only for a feature that is on. */
    java.util.Set<Feature> featuresOn(Viewer viewer);
}
