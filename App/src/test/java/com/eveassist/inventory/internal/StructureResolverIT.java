package com.eveassist.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.IntegrationTest;
import com.eveassist.characters.LinkedCharacter;
import com.eveassist.esi.CharacterAsset;
import com.eveassist.esi.StructureLookup;
import com.eveassist.identity.Viewer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Which structures get asked about, how often, and how few refusals a run takes. */
class StructureResolverIT extends IntegrationTest {

    private static final String BOTH_SCOPES_ASSETS = "esi-assets.read_assets.v1";
    private static final String STRUCTURES = "esi-universe.read_structures.v1";
    private static final long FIRST = 1_000_000_000_000L;

    @Autowired
    StructureResolver resolver;

    @Autowired
    StructureRepository structures;

    @Autowired
    AssetFetcher fetcher;

    @Autowired
    JdbcClient jdbc;

    private LinkedCharacter link(Viewer viewer, String state, String... scopes) {
        var id = UUID.randomUUID();
        var eveId = 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000);
        jdbc.sql("""
                INSERT INTO character (public_id, owner_account_id, eve_character_id, name, owner_hash, scopes, state)
                VALUES (:id, :owner, :eve, 'Alt', 'hash', :scopes, :state)
                """)
                .param("id", id).param("owner", viewer.accountId().value()).param("eve", eveId)
                .param("scopes", scopes).param("state", state).update();
        return new LinkedCharacter(id, eveId, "Alt", Set.of(scopes), "NEEDS_REAUTHORIZATION".equals(state));
    }

    private LinkedCharacter linkActive(Viewer viewer) {
        return link(viewer, "ACTIVE", BOTH_SCOPES_ASSETS, STRUCTURES);
    }

    private void insertAsset(LinkedCharacter character, long itemId, long locationId, String locationType) {
        jdbc.sql("""
                INSERT INTO character_asset (character_id, item_id, type_id, quantity, location_id, location_type,
                    location_flag, is_singleton)
                VALUES (:id, :item, 34, 1, :location, :type, 'Hangar', false)
                """)
                .param("id", character.id()).param("item", itemId).param("location", locationId)
                .param("type", locationType).update();
    }

    /** Items in the given structures, typed `item` as real ESI types them. */
    private void itemsIn(LinkedCharacter character, long... structureIds) {
        long item = 1;
        for (var structureId : structureIds) {
            insertAsset(character, item++, structureId, "item");
        }
    }

    private void backdate(LinkedCharacter character, long structureId, int days) {
        jdbc.sql("""
                UPDATE character_structure SET asked_at = asked_at - make_interval(days => :days)
                WHERE character_id = :id AND structure_id = :structure
                """)
                .param("days", days).param("id", character.id()).param("structure", structureId).update();
    }

    private String nameOf(LinkedCharacter character, long structureId) {
        return jdbc.sql("SELECT name FROM character_structure WHERE character_id = :id AND structure_id = :s")
                .param("id", character.id()).param("s", structureId).query(String.class).optional().orElse(null);
    }

    @Test
    void namesTheStructuresWhereTheCharacterHasItemsWhateverTheLocationTypeSays() {
        var character = linkActive(viewerFor(newSub()));
        itemsIn(character, FIRST + 1, FIRST + 2);
        esi.structure(FIRST + 1, StructureLookup.found("Alpha Keepstar", 30000142));
        esi.structure(FIRST + 2, StructureLookup.found("Beta Raitaru", 30002187));

        resolver.resolve(character);

        assertThat(nameOf(character, FIRST + 1)).isEqualTo("Alpha Keepstar");
        assertThat(nameOf(character, FIRST + 2)).isEqualTo("Beta Raitaru");
        assertThat(structures.counts(character.id())).isEqualTo(new StructureRepository.Counts(2, 2));
        assertThat(esi.structuresAsked()).containsExactlyInAnyOrder(character.eveCharacterId() + ":" + (FIRST + 1),
                character.eveCharacterId() + ":" + (FIRST + 2));
    }

    @Test
    void doesNotAskAboutStationsSystemsOrContainersInsideAStructure() {
        var character = linkActive(viewerFor(newSub()));
        insertAsset(character, 10, 60003760L, "station");
        insertAsset(character, 11, 30000142L, "solar_system");
        insertAsset(character, 12, FIRST + 5, "other");
        // a container in the structure, and an item in that container
        insertAsset(character, FIRST + 100, FIRST + 5, "item");
        insertAsset(character, 13, FIRST + 100, "item");
        esi.structure(FIRST + 5, StructureLookup.found("Gamma", 30000142));

        resolver.resolve(character);

        // only the structure itself: not the station, the system or the container, which is an item of its own
        assertThat(esi.structuresAsked()).containsExactly(character.eveCharacterId() + ":" + (FIRST + 5));
    }

    @Test
    void asksOnceForAStructureWithManyItemsInIt() {
        var character = linkActive(viewerFor(newSub()));
        for (int i = 1; i <= 5; i++) {
            insertAsset(character, i, FIRST + 7, "item");
        }
        esi.structure(FIRST + 7, StructureLookup.found("Delta", 30000142));

        resolver.resolve(character);

        assertThat(esi.structuresAsked()).hasSize(1);
    }

    @Test
    void doesNotAskAgainWhileAnAnswerIsCurrentAndAsksAgainWhenItIsOld() {
        var character = linkActive(viewerFor(newSub()));
        itemsIn(character, FIRST + 1, FIRST + 2, FIRST + 3);
        esi.structure(FIRST + 1, StructureLookup.found("Alpha", 30000142));
        esi.structure(FIRST + 2, StructureLookup.noAccess());
        esi.structure(FIRST + 3, StructureLookup.notFound());
        resolver.resolve(character);
        assertThat(esi.structuresAsked()).hasSize(3);

        resolver.resolve(character);
        assertThat(esi.structuresAsked()).hasSize(3);

        backdate(character, FIRST + 1, 6);
        backdate(character, FIRST + 2, 2);
        backdate(character, FIRST + 3, 6);
        resolver.resolve(character);
        // a name is trusted for 7 days, a refusal for 1 day, a missing structure for 7 days
        assertThat(esi.structuresAsked()).hasSize(4)
                .last().isEqualTo(character.eveCharacterId() + ":" + (FIRST + 2));

        backdate(character, FIRST + 1, 2);
        backdate(character, FIRST + 3, 2);
        resolver.resolve(character);
        assertThat(esi.structuresAsked()).hasSize(6);
    }

    @Test
    void keepsAnOldNameWhenTheStructureIsAskedAgainAndAnswersWithANewOne() {
        var character = linkActive(viewerFor(newSub()));
        itemsIn(character, FIRST + 1);
        esi.structure(FIRST + 1, StructureLookup.found("Old Name", 30000142));
        resolver.resolve(character);
        backdate(character, FIRST + 1, 8);
        esi.structure(FIRST + 1, StructureLookup.found("New Name", 30000142));

        resolver.resolve(character);

        assertThat(nameOf(character, FIRST + 1)).isEqualTo("New Name");
    }

    @Test
    void stopsAfterAFewRefusalsSoTheErrorLimitIsNotSpent() {
        var character = linkActive(viewerFor(newSub()));
        var ids = new long[25];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = FIRST + 10 + i;
        }
        itemsIn(character, ids);

        resolver.resolve(character);

        assertThat(esi.structuresAsked()).hasSize(StructureResolver.MOST_REFUSALS_PER_RUN);
        // the next run goes on with structures it has not asked about, and leaves the refused ones alone
        resolver.resolve(character);
        assertThat(esi.structuresAsked()).hasSize(2 * StructureResolver.MOST_REFUSALS_PER_RUN);
        assertThat(esi.structuresAsked()).doesNotHaveDuplicates();
    }

    @Test
    void asksAboutNewStructuresBeforeOldOnesAndNeverMoreThanTheLimitInOneRun() {
        var character = linkActive(viewerFor(newSub()));
        var many = new ArrayList<Long>();
        for (int i = 0; i < 40; i++) {
            many.add(FIRST + 1_000 + i);
        }
        itemsIn(character, many.stream().mapToLong(Long::longValue).toArray());
        many.forEach(id -> esi.structure(id, StructureLookup.found("S" + id, 30000142)));

        resolver.resolve(character);

        assertThat(esi.structuresAsked()).hasSize(StructureResolver.MOST_ASKED_PER_RUN);
        resolver.resolve(character);
        assertThat(esi.structuresAsked()).hasSize(40).doesNotHaveDuplicates();
        assertThat(structures.counts(character.id())).isEqualTo(new StructureRepository.Counts(40, 40));
    }

    @Test
    void aFailureStopsTheRunAndKeepsNothingSoTheNextRunTriesAgain() {
        var character = linkActive(viewerFor(newSub()));
        itemsIn(character, FIRST + 1, FIRST + 2);
        esi.structure(FIRST + 1, StructureLookup.failed("ESI is throttling universe-structure"));
        esi.structure(FIRST + 2, StructureLookup.found("Beta", 30000142));

        resolver.resolve(character);

        assertThat(esi.structuresAsked()).hasSize(1);
        assertThat(structures.counts(character.id())).isEqualTo(new StructureRepository.Counts(2, 0));
        esi.structure(FIRST + 1, StructureLookup.found("Alpha", 30000142));
        resolver.resolve(character);
        assertThat(structures.counts(character.id())).isEqualTo(new StructureRepository.Counts(2, 2));
    }

    @Test
    void doesNothingForACharacterThatNeedsAuthorizationOrLacksTheScope() {
        var viewer = viewerFor(newSub());
        var refused = link(viewer, "NEEDS_REAUTHORIZATION", BOTH_SCOPES_ASSETS, STRUCTURES);
        var noScope = link(viewer, "ACTIVE", BOTH_SCOPES_ASSETS);
        itemsIn(refused, FIRST + 1);
        itemsIn(noScope, FIRST + 1);

        resolver.resolve(refused);
        resolver.resolve(noScope);

        assertThat(esi.structuresAsked()).isEmpty();
    }

    @Test
    void whatOneCharacterLearnedIsNeverUsedForAnother() {
        var viewer = viewerFor(newSub());
        var first = linkActive(viewer);
        var second = linkActive(viewer);
        itemsIn(first, FIRST + 1);
        itemsIn(second, FIRST + 1);
        esi.structure(FIRST + 1, StructureLookup.found("Alpha", 30000142));

        resolver.resolve(first);

        assertThat(nameOf(first, FIRST + 1)).isEqualTo("Alpha");
        assertThat(nameOf(second, FIRST + 1)).isNull();
        assertThat(structures.counts(second.id()).named()).isZero();
    }

    @Test
    void theNamesGoWhenTheCharacterGoes() {
        var character = linkActive(viewerFor(newSub()));
        itemsIn(character, FIRST + 1);
        esi.structure(FIRST + 1, StructureLookup.found("Alpha", 30000142));
        resolver.resolve(character);

        jdbc.sql("DELETE FROM character WHERE public_id = :id").param("id", character.id()).update();

        assertThat(jdbc.sql("SELECT count(*) FROM character_structure WHERE character_id = :id")
                .param("id", character.id()).query(Integer.class).single()).isZero();
    }

    @Test
    void theDatabaseRejectsAFoundStructureWithNoName() {
        var character = linkActive(viewerFor(newSub()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO character_structure (character_id, structure_id, status, asked_at)
                VALUES (:id, 1, 'FOUND', now())
                """).param("id", character.id()).update())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void aFetchNamesStructuresAfterStoringAssetsAndAlsoWhenEsiSaysNothingChanged() {
        var viewer = viewerFor(newSub());
        var character = linkActive(viewer);
        esi.assetsOf(character.eveCharacterId(), List.of(
                new CharacterAsset(1, 34, 5, FIRST + 1, "item", "Hangar", false, null)));
        esi.structure(FIRST + 1, StructureLookup.found("Alpha", 30000142));

        fetcher.fetch(character);

        assertThat(nameOf(character, FIRST + 1)).isEqualTo("Alpha");

        esi.assetsOf(character.eveCharacterId(), List.of(
                new CharacterAsset(1, 34, 5, FIRST + 1, "item", "Hangar", false, null),
                new CharacterAsset(2, 34, 5, FIRST + 2, "item", "Hangar", false, null)));
        esi.structure(FIRST + 2, StructureLookup.found("Beta", 30000142));
        fetcher.fetch(character);
        assertThat(nameOf(character, FIRST + 2)).isEqualTo("Beta");

        // a character whose assets are unchanged still finishes names an earlier run did not reach
        insertAsset(character, 3, FIRST + 3, "item");
        esi.structure(FIRST + 3, StructureLookup.found("Gamma", 30000142));
        esi.assetsUnchanged(character.eveCharacterId());
        fetcher.fetch(character);
        assertThat(nameOf(character, FIRST + 3)).isEqualTo("Gamma");
    }

    @Test
    void aFailedAssetFetchAsksNoStructureNames() {
        var character = linkActive(viewerFor(newSub()));
        insertAsset(character, 1, FIRST + 1, "item");
        esi.failAssets(character.eveCharacterId(), "ESI could not be reached");

        fetcher.fetch(character);

        assertThat(esi.structuresAsked()).isEmpty();
    }
}
