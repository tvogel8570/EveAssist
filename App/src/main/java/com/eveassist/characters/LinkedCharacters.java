package com.eveassist.characters;

import com.eveassist.identity.Viewer;
import java.util.List;

/** What other modules may ask the {@code characters} module. Tokens never leave it. */
public interface LinkedCharacters {

    /** The viewer's linked characters, by name. Never another account's. */
    List<LinkedCharacter> of(Viewer viewer);
}
