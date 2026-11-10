package com.eveassist.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.eveassist.IntegrationTest;
import com.eveassist.esi.CharacterAsset;
import com.eveassist.identity.Viewer;
import com.eveassist.inventory.internal.AssetFetcher.Phase;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class AssetFetcherIT extends IntegrationTest {

    static final String ASSETS_SCOPE = "esi-assets.read_assets.v1";
    static final String STRUCTURES_SCOPE = "esi-universe.read_structures.v1";

    @Autowired
    AssetFetcher fetcher;

    @Autowired
    JdbcClient jdbc;

    /** A linked character, written straight into the table, because the module under test never creates characters. */
    record Linked(UUID id, long eveId) {
    }

    private Linked link(Viewer viewer, String name, String state, String... scopes) {
        var id = UUID.randomUUID();
        var eveId = 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000);
        jdbc.sql("""
                INSERT INTO character (public_id, owner_account_id, eve_character_id, name, owner_hash, scopes, state)
                VALUES (:id, :owner, :eve, :name, 'hash', :scopes, :state)
                """)
                .param("id", id).param("owner", viewer.accountId().value()).param("eve", eveId).param("name", name)
                .param("scopes", scopes).param("state", state)
                .update();
        return new Linked(id, eveId);
    }

    private Linked linkActive(Viewer viewer, String name) {
        return link(viewer, name, "ACTIVE", ASSETS_SCOPE, STRUCTURES_SCOPE);
    }

    private static CharacterAsset stack(long itemId, int typeId, long quantity) {
        return new CharacterAsset(itemId, typeId, quantity, 60003760L, "station", "Hangar", false, null);
    }

    private int storedRows(UUID characterId) {
        return jdbc.sql("SELECT count(*) FROM character_asset WHERE character_id = :id").param("id", characterId)
                .query(Integer.class).single();
    }

    private void awaitIdle(Viewer viewer) {
        await().atMost(10, TimeUnit.SECONDS).until(() -> !fetcher.anyRunning(viewer));
    }

    private AssetFetcher.CharacterAssets statusOf(Viewer viewer, UUID characterId) {
        return fetcher.status(viewer).stream().filter(s -> s.characterId().equals(characterId)).findFirst()
                .orElseThrow();
    }

    @Test
    void storesTheAssetsAndRecordsTheFetch() {
        var viewer = viewerFor(newSub());
        var character = linkActive(viewer, "Alt One");
        esi.assetsOf(character.eveId(), List.of(stack(1, 34, 1500),
                new CharacterAsset(2, 587, 1, 1034323745897L, "other", "Hangar", true, null),
                new CharacterAsset(3, 1000, 1, 2, "item", "Unlocked", true, true)));

        fetcher.refresh(viewer);
        awaitIdle(viewer);

        assertThat(storedRows(character.id())).isEqualTo(3);
        var status = statusOf(viewer, character.id());
        assertThat(status.phase()).isEqualTo(Phase.CURRENT);
        assertThat(status.itemCount()).isEqualTo(3);
        assertThat(status.fetchedAt()).isNotNull();
        var row = jdbc.sql("SELECT * FROM character_asset WHERE character_id = :id AND item_id = 3")
                .param("id", character.id()).query().singleRow();
        assertThat(row).containsEntry("location_id", 2L).containsEntry("location_type", "item")
                .containsEntry("blueprint_copy", true).containsEntry("is_singleton", true);
        assertThat(jdbc.sql("SELECT blueprint_copy FROM character_asset WHERE item_id = 1 AND character_id = :id")
                .param("id", character.id()).query(Boolean.class).optional()).isEmpty();
    }

    @Test
    void eachFetchReplacesTheCharactersRowsAndKeepsNoHistory() {
        var viewer = viewerFor(newSub());
        var character = linkActive(viewer, "Alt One");
        esi.assetsOf(character.eveId(), List.of(stack(1, 34, 10), stack(2, 35, 20), stack(3, 36, 30)));
        fetcher.refresh(viewer);
        awaitIdle(viewer);

        esi.assetsOf(character.eveId(), List.of(stack(2, 35, 5), stack(9, 37, 1)));
        fetcher.refresh(viewer);
        awaitIdle(viewer);

        var items = jdbc.sql("SELECT item_id FROM character_asset WHERE character_id = :id ORDER BY item_id")
                .param("id", character.id()).query(Long.class).list();
        assertThat(items).containsExactly(2L, 9L);
        assertThat(statusOf(viewer, character.id()).itemCount()).isEqualTo(2);
    }

    @Test
    void storesMoreRowsThanOneBatch() {
        var viewer = viewerFor(newSub());
        var character = linkActive(viewer, "Big Hangar");
        var many = new ArrayList<CharacterAsset>();
        for (int i = 1; i <= 2_500; i++) {
            many.add(stack(i, 34 + i % 3, i));
        }
        esi.assetsOf(character.eveId(), many);

        fetcher.refresh(viewer);
        awaitIdle(viewer);

        assertThat(storedRows(character.id())).isEqualTo(2_500);
    }

    @Test
    void keepsTheStoredRowsWhenEsiSaysTheCopyIsStillCurrent() {
        var viewer = viewerFor(newSub());
        var character = linkActive(viewer, "Alt One");
        esi.assetsOf(character.eveId(), List.of(stack(1, 34, 10), stack(2, 35, 20)));
        fetcher.refresh(viewer);
        awaitIdle(viewer);
        var changedBefore = jdbc.sql("SELECT changed_at FROM character_fetch WHERE character_id = :id")
                .param("id", character.id()).query(java.sql.Timestamp.class).single();

        esi.assetsUnchanged(character.eveId());
        fetcher.refresh(viewer);
        awaitIdle(viewer);

        assertThat(storedRows(character.id())).isEqualTo(2);
        var status = statusOf(viewer, character.id());
        assertThat(status.phase()).isEqualTo(Phase.CURRENT);
        assertThat(status.itemCount()).isEqualTo(2);
        var changedAfter = jdbc.sql("SELECT changed_at FROM character_fetch WHERE character_id = :id")
                .param("id", character.id()).query(java.sql.Timestamp.class).single();
        assertThat(changedAfter).isEqualTo(changedBefore);
    }

    @Test
    void keepsTheStoredRowsAndSaysSoWhenAFetchFails() {
        var viewer = viewerFor(newSub());
        var character = linkActive(viewer, "Alt One");
        esi.assetsOf(character.eveId(), List.of(stack(1, 34, 10), stack(2, 35, 20)));
        fetcher.refresh(viewer);
        awaitIdle(viewer);

        esi.failAssets(character.eveId(), "ESI is throttling char-asset for another 9 s");
        fetcher.refresh(viewer);
        awaitIdle(viewer);

        assertThat(storedRows(character.id())).isEqualTo(2);
        var status = statusOf(viewer, character.id());
        assertThat(status.phase()).isEqualTo(Phase.FAILED);
        assertThat(status.problem()).contains("throttling");
        assertThat(status.itemCount()).isEqualTo(2);
        assertThat(status.fetchedAt()).isNotNull();
    }

    @Test
    void recordsAFailureForACharacterThatWasNeverFetched() {
        var viewer = viewerFor(newSub());
        var character = linkActive(viewer, "Alt One");
        esi.failAssets(character.eveId(), "ESI could not be reached");

        fetcher.refresh(viewer);
        awaitIdle(viewer);

        var status = statusOf(viewer, character.id());
        assertThat(status.phase()).isEqualTo(Phase.FAILED);
        assertThat(status.fetchedAt()).isNull();
        assertThat(status.itemCount()).isNull();
    }

    @Test
    void doesNotAskEsiAboutACharacterThatNeedsAuthorizationOrLacksTheScope() {
        var viewer = viewerFor(newSub());
        var refused = link(viewer, "Refused", "NEEDS_REAUTHORIZATION", ASSETS_SCOPE);
        var noScope = link(viewer, "No Scope", "ACTIVE", STRUCTURES_SCOPE);

        fetcher.refresh(viewer);
        awaitIdle(viewer);

        assertThat(esi.assetsAsked()).isEmpty();
        assertThat(statusOf(viewer, refused.id()).phase()).isEqualTo(Phase.NEEDS_REAUTHORIZATION);
        assertThat(statusOf(viewer, noScope.id()).phase()).isEqualTo(Phase.MISSING_SCOPE);
    }

    @Test
    void neverFetchesOrShowsAnotherAccountsCharacters() {
        var other = viewerFor(newSub());
        var theirs = linkActive(other, "Theirs");
        esi.assetsOf(theirs.eveId(), List.of(stack(1, 34, 10)));
        var viewer = viewerFor(newSub());
        linkActive(viewer, "Mine");

        fetcher.refresh(viewer);
        awaitIdle(viewer);

        assertThat(esi.assetsAsked()).doesNotContain(theirs.eveId());
        assertThat(fetcher.status(viewer)).extracting(AssetFetcher.CharacterAssets::name).containsExactly("Mine");
        assertThat(storedRows(theirs.id())).isZero();
    }

    @Test
    void skipsACharacterThatIsAlreadyBeingFetched() throws Exception {
        var viewer = viewerFor(newSub());
        var character = linkActive(viewer, "Alt One");
        var gate = new CountDownLatch(1);
        esi.holdAssetCalls(gate);

        fetcher.refresh(viewer);
        await().atMost(5, TimeUnit.SECONDS).until(() -> fetcher.anyRunning(viewer));
        assertThat(statusOf(viewer, character.id()).phase()).isEqualTo(Phase.RUNNING);
        fetcher.refresh(viewer);
        fetcher.refresh(viewer);
        gate.countDown();
        awaitIdle(viewer);

        assertThat(esi.assetsAsked()).containsExactly(character.eveId());
    }

    @Test
    void removingACharacterRemovesItsAssetsAndFetchRecord() {
        var viewer = viewerFor(newSub());
        var character = linkActive(viewer, "Alt One");
        esi.assetsOf(character.eveId(), List.of(stack(1, 34, 10)));
        fetcher.refresh(viewer);
        awaitIdle(viewer);

        jdbc.sql("DELETE FROM character WHERE public_id = :id").param("id", character.id()).update();

        assertThat(storedRows(character.id())).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM character_fetch WHERE character_id = :id")
                .param("id", character.id()).query(Integer.class).single()).isZero();
    }

    @Test
    void anUnexpectedFailureForACharacterThatWasRemovedIsHandledNotThrown() {
        var gone = new com.eveassist.characters.LinkedCharacter(UUID.randomUUID(), 2_100_000_001L, "Gone",
                java.util.Set.of(ASSETS_SCOPE), false);
        esi.throwOnAssets(new IllegalStateException("boom"));

        fetcher.fetch(gone);

        assertThat(storedRows(gone.id())).isZero();
    }

    @Test
    void anUnexpectedFailureIsRecordedForACharacterThatStillExists() {
        var viewer = viewerFor(newSub());
        var character = linkActive(viewer, "Alt One");
        esi.throwOnAssets(new IllegalStateException("boom"));

        fetcher.refresh(viewer);
        awaitIdle(viewer);

        var status = statusOf(viewer, character.id());
        assertThat(status.phase()).isEqualTo(Phase.FAILED);
        assertThat(status.problem()).contains("unexpectedly").doesNotContain("boom");
    }

    @Test
    void aFetchThatFindsItsCharacterGoneLeavesNothingBehind() {
        var viewer = viewerFor(newSub());
        var gone = new com.eveassist.characters.LinkedCharacter(UUID.randomUUID(), 2_100_000_000L, "Gone",
                java.util.Set.of(ASSETS_SCOPE), false);
        esi.assetsOf(gone.eveCharacterId(), List.of(stack(1, 34, 10)));

        fetcher.fetch(gone);

        assertThat(storedRows(gone.id())).isZero();
        assertThat(fetcher.status(viewer)).isEmpty();
    }
}
