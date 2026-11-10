package com.eveassist.characters.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.characters.Feature;
import com.eveassist.characters.LinkedCharacters;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Which corporation a character is in and whether it is a Director is a fact about the character, so the characters
 * module learns it from ESI and other modules read it from {@code LinkedCharacters}.
 */
@Import(TestCharactersConfig.class)
class MembershipLearnerIT extends IntegrationTest {

    private static final Set<String> WITH_ROLES = Set.of("esi-assets.read_assets.v1",
            "esi-characters.read_corporation_roles.v1");
    private static final Set<String> WITHOUT_ROLES = Feature.INVENTORY.scopes();

    @Autowired
    LinkedCharacters linked;

    @Autowired
    CharacterRepository characters;

    @Autowired
    TokenCipher cipher;

    private long link(com.eveassist.identity.Viewer viewer, String name, Set<String> scopes) {
        var eveId = 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000);
        characters.link(viewer.accountId(), eveId, name, "hash", scopes, cipher.encrypt(eveId, "refresh"));
        return eveId;
    }

    private com.eveassist.characters.LinkedCharacter of(com.eveassist.identity.Viewer viewer, long eveId) {
        return linked.of(viewer).stream().filter(c -> c.eveCharacterId() == eveId).findFirst().orElseThrow();
    }

    @Test
    void learnsTheCorporationAndTheDirectorRoleOfCharactersThatGrantedTheRolesPermission() {
        var viewer = viewerFor(newSub());
        var boss = link(viewer, "Boss", WITH_ROLES);
        var member = link(viewer, "Member", WITH_ROLES);
        esi.inCorporation(boss, 98000001L, "Director");
        esi.inCorporation(member, 98000001L, "Hangar_Take_1");

        linked.learnCorporations(viewer);

        assertThat(of(viewer, boss).eveCorporationId()).isEqualTo(98000001L);
        assertThat(of(viewer, boss).director()).isTrue();
        assertThat(of(viewer, member).eveCorporationId()).isEqualTo(98000001L);
        assertThat(of(viewer, member).director()).isFalse();
    }

    @Test
    void aCharacterThatCannotSayIsLeftUnknownAndNotAskedAbout() {
        var viewer = viewerFor(newSub());
        var old = link(viewer, "Old Token", WITHOUT_ROLES);
        esi.inCorporation(old, 98000001L, "Director");

        linked.learnCorporations(viewer);

        assertThat(of(viewer, old).eveCorporationId()).isNull();
        assertThat(of(viewer, old).director()).isNull();
    }

    @Test
    void aFailedLookupLeavesTheCharacterUnknownAndKeepsWhatWasKnown() {
        var viewer = viewerFor(newSub());
        var boss = link(viewer, "Boss", WITH_ROLES);
        esi.inCorporation(boss, 98000001L, "Director");
        linked.learnCorporations(viewer);
        esi.reset();

        linked.learnCorporations(viewer);

        assertThat(of(viewer, boss).director()).isTrue();
    }

    @Test
    void anotherAccountNeverSeesWhatWasLearned() {
        var viewer = viewerFor(newSub());
        var stranger = viewerFor(newSub());
        var boss = link(viewer, "Boss", WITH_ROLES);
        esi.inCorporation(boss, 98000001L, "Director");

        linked.learnCorporations(viewer);

        assertThat(linked.of(stranger)).isEmpty();
        assertThat(linked.of(viewer)).singleElement().satisfies(c -> assertThat(c.director()).isTrue());
    }
}
