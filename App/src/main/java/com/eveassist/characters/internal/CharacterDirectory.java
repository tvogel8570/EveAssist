package com.eveassist.characters.internal;

import com.eveassist.characters.LinkedCharacter;
import com.eveassist.characters.LinkedCharacters;
import com.eveassist.identity.Viewer;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class CharacterDirectory implements LinkedCharacters {

    private final CharacterRepository characters;
    private final FeatureRepository features;

    CharacterDirectory(CharacterRepository characters, FeatureRepository features) {
        this.characters = characters;
        this.features = features;
    }

    @Override
    public java.util.Set<com.eveassist.characters.Feature> featuresOn(Viewer viewer) {
        return features.turnedOn(viewer.accountId());
    }

    @Override
    public List<LinkedCharacter> of(Viewer viewer) {
        return characters.listFor(viewer.accountId()).stream()
                .map(row -> new LinkedCharacter(row.id(), row.eveCharacterId(), row.name(), row.scopes(),
                        "NEEDS_REAUTHORIZATION".equals(row.state()), row.linkedAt()))
                .toList();
    }
}
