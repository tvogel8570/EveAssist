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
    private final MembershipRepository memberships;
    private final MembershipLearner learner;

    CharacterDirectory(CharacterRepository characters, FeatureRepository features, MembershipRepository memberships,
            MembershipLearner learner) {
        this.characters = characters;
        this.features = features;
        this.memberships = memberships;
        this.learner = learner;
    }

    @Override
    public void learnCorporations(Viewer viewer) {
        learner.learn(characters.listFor(viewer.accountId()));
    }

    @Override
    public java.util.Set<com.eveassist.characters.Feature> featuresOn(Viewer viewer) {
        return features.turnedOn(viewer.accountId());
    }

    @Override
    public List<LinkedCharacter> of(Viewer viewer) {
        var rows = characters.listFor(viewer.accountId());
        var known = memberships.findAll(rows.stream().map(CharacterDetails::id).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(MembershipRepository.Membership::characterId,
                        java.util.function.Function.identity()));
        return rows.stream().map(row -> {
            var membership = known.get(row.id());
            return new LinkedCharacter(row.id(), row.eveCharacterId(), row.name(), row.scopes(),
                    "NEEDS_REAUTHORIZATION".equals(row.state()), row.linkedAt(),
                    membership == null ? null : membership.eveCorporationId(),
                    membership == null ? null : membership.director());
        }).toList();
    }
}
