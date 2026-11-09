package com.eveassist.characters.internal;

import com.eveassist.characters.Feature;
import com.eveassist.identity.Viewer;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** What the Characters page does: list a viewer's characters, turn features on and off, remove, show a portrait. */
@Service
public class CharacterService {

    private final CharacterRepository characters;
    private final FeatureRepository features;

    CharacterService(CharacterRepository characters, FeatureRepository features) {
        this.characters = characters;
        this.features = features;
    }

    public List<CharacterView> list(Viewer viewer) {
        var needed = neededScopes(features.turnedOn(viewer.accountId()));
        return characters.listFor(viewer.accountId()).stream().map(details -> {
            var missing = new TreeSet<>(needed);
            missing.removeAll(details.scopes());
            var status = "NEEDS_REAUTHORIZATION".equals(details.state()) ? CharacterView.Status.REAUTHORIZE
                    : missing.isEmpty() ? CharacterView.Status.ACTIVE : CharacterView.Status.NEEDS_PERMISSIONS;
            return new CharacterView(details.id(), details.name(), status,
                    List.copyOf(new TreeSet<>(details.scopes())), List.copyOf(missing), details.hasPortrait());
        }).toList();
    }

    public Set<Feature> featuresOn(Viewer viewer) {
        return features.turnedOn(viewer.accountId());
    }

    public void setFeature(Viewer viewer, Feature feature, boolean on) {
        if (on) {
            features.turnOn(viewer.accountId(), feature);
        } else {
            features.turnOff(viewer.accountId(), feature);
        }
    }

    /** Deletes the character with its token at once. False when it is not the viewer's. */
    public boolean remove(Viewer viewer, UUID characterId) {
        return characters.remove(viewer.accountId(), characterId);
    }

    /** The portrait, only for the character's owner. */
    public Optional<byte[]> portrait(Viewer viewer, UUID characterId) {
        return characters.findPortrait(viewer.accountId(), characterId);
    }

    private static Set<String> neededScopes(Set<Feature> on) {
        var needed = new TreeSet<String>();
        on.forEach(feature -> needed.addAll(feature.scopes()));
        return needed;
    }
}
