package com.eveassist.characters.internal;

import com.eveassist.characters.LinkedCharacter;
import com.eveassist.characters.LinkedCharacters;
import com.eveassist.identity.Viewer;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class CharacterDirectory implements LinkedCharacters {

    private final CharacterRepository characters;

    CharacterDirectory(CharacterRepository characters) {
        this.characters = characters;
    }

    @Override
    public List<LinkedCharacter> of(Viewer viewer) {
        return characters.listFor(viewer.accountId()).stream()
                .map(row -> new LinkedCharacter(row.id(), row.eveCharacterId(), row.name(), row.scopes(),
                        "NEEDS_REAUTHORIZATION".equals(row.state()), row.linkedAt()))
                .toList();
    }
}
