package com.eveassist.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.eveassist.IntegrationTest;
import com.eveassist.characters.Feature;
import com.eveassist.esi.CharacterAsset;
import com.eveassist.identity.Viewer;
import com.eveassist.inventory.internal.CorporationFetcher.Phase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Reading the assets of the corporations an account's own characters belong to, through a Director of each. */
class CorporationFetcherIT extends IntegrationTest {

    static final String[] CORPORATION_SCOPES = {"esi-assets.read_assets.v1", "esi-universe.read_structures.v1",
            "esi-assets.read_corporation_assets.v1", "esi-corporations.read_divisions.v1",
            "esi-characters.read_corporation_roles.v1"};
    private static final long STATION = 60003760L;
    private static final long STRUCTURE = 1_040_000_000_555L;
    private static final long OFFICE = 1_050_000_000_001L;

    @Autowired
    CorporationFetcher fetcher;

    @Autowired
    CorporationRepository corporations;

    @Autowired
    JdbcClient jdbc;

    record Linked(UUID id, long eveId) {
    }

    private static long corporationId() {
        return 98_000_000L + ThreadLocalRandom.current().nextInt(1_000_000);
    }

    private Linked link(Viewer viewer, String name, String... scopes) {
        var id = UUID.randomUUID();
        var eveId = 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000);
        jdbc.sql("""
                INSERT INTO character (public_id, owner_account_id, eve_character_id, name, owner_hash, scopes, state)
                VALUES (:id, :owner, :eve, :name, 'hash', :scopes, 'ACTIVE')
                """)
                .param("id", id).param("owner", viewer.accountId().value()).param("eve", eveId).param("name", name)
                .param("scopes", scopes).update();
        return new Linked(id, eveId);
    }

    private Linked linkWithCorporationScopes(Viewer viewer, String name) {
        return link(viewer, name, CORPORATION_SCOPES);
    }

    private void turnOn(Viewer viewer) {
        jdbc.sql("INSERT INTO account_feature (owner_account_id, feature) VALUES (:owner, :feature) "
                        + "ON CONFLICT DO NOTHING")
                .param("owner", viewer.accountId().value()).param("feature", Feature.INVENTORY.name()).update();
        jdbc.sql("INSERT INTO account_feature (owner_account_id, feature) VALUES (:owner, :feature) "
                        + "ON CONFLICT DO NOTHING")
                .param("owner", viewer.accountId().value()).param("feature", Feature.CORPORATION_ASSETS.name())
                .update();
    }

    private static CharacterAsset office() {
        return new CharacterAsset(OFFICE, 27, 1, STRUCTURE, "other", "OfficeFolder", true, null);
    }

    private static CharacterAsset inOffice(long itemId, int typeId, long quantity, String division) {
        return new CharacterAsset(itemId, typeId, quantity, OFFICE, "item", division, false, null);
    }

    /** What the characters module has learned about a character's corporation, read straight from its table. */
    record Learned(long eveCorporationId, boolean director, Instant checkedAt, Instant corporationCheckedAt) {
    }

    private Learned membership(UUID characterId) {
        return jdbc.sql("SELECT * FROM character_corporation WHERE character_id = :id").param("id", characterId)
                .query((rs, row) -> new Learned(rs.getLong("eve_corporation_id"), rs.getBoolean("director"),
                        rs.getTimestamp("checked_at").toInstant(), rs.getTimestamp("corporation_checked_at").toInstant()))
                .single();
    }

    private int membershipCount(UUID characterId) {
        return jdbc.sql("SELECT count(*) FROM character_corporation WHERE character_id = :id")
                .param("id", characterId).query(Integer.class).single();
    }

    private int storedRows(UUID corporationId) {
        return jdbc.sql("SELECT count(*) FROM corporation_asset WHERE corporation_id = :id")
                .param("id", corporationId).query(Integer.class).single();
    }

    private CorporationFetcher.CorporationAssets only(Viewer viewer) {
        var status = fetcher.status(viewer);
        assertThat(status).hasSize(1);
        return status.getFirst();
    }

    @Test
    void aDirectorReadsTheCorporationsAssetsAndTheHangarNames() {
        var viewer = viewerFor(newSub());
        turnOn(viewer);
        var corporation = corporationId();
        var boss = linkWithCorporationScopes(viewer, "The Director");
        esi.inCorporation(boss.eveId(), corporation, "Director");
        esi.corporationNamed(corporation, "Test Industries");
        esi.hangars(corporation, Map.of(2, "Production", 3, "Ammo"));
        esi.corporationAssetsOf(corporation, List.of(office(), inOffice(1, 34, 900, "CorpSAG2"),
                inOffice(2, 35, 20, "CorpSAG3")));

        fetcher.run(viewer);

        var status = only(viewer);
        assertThat(status.name()).isEqualTo("Test Industries");
        assertThat(status.phase()).isEqualTo(Phase.CURRENT);
        assertThat(status.itemCount()).isEqualTo(2);
        assertThat(status.via()).isEqualTo("The Director");
        assertThat(esi.corporationAssetsAsked()).containsExactly(corporation + ":" + boss.eveId());
        var id = status.corporationId();
        assertThat(storedRows(id)).isEqualTo(2);
        var row = jdbc.sql("SELECT * FROM corporation_asset WHERE corporation_id = :id AND item_id = 1")
                .param("id", id).query().singleRow();
        assertThat(row).containsEntry("division", 2).containsEntry("root_location_id", STRUCTURE)
                .containsEntry("held", "HANGAR").containsEntry("depth", 0);
        assertThat(corporations.divisionNames(List.of(id)).get(id)).containsOnly(Map.entry(2, "Production"),
                Map.entry(3, "Ammo"));
    }

    /** As for a character's assets: an ETag that outlived the rows must not make "unchanged" the answer for ever. */
    @Test
    void aCorporationWhoseEtagOutlivedItsRowsIsFetchedInFull() {
        var viewer = viewerFor(newSub());
        turnOn(viewer);
        var corporation = corporationId();
        var boss = linkWithCorporationScopes(viewer, "The Director");
        esi.inCorporation(boss.eveId(), corporation, "Director");
        esi.corporationNamed(corporation, "Test Industries");
        esi.corporationAssetsUnchangedUntilForgotten(corporation, List.of(office(), inOffice(1, 34, 900, "CorpSAG2")));

        fetcher.run(viewer);

        var status = only(viewer);
        assertThat(status.phase()).isEqualTo(Phase.CURRENT);
        assertThat(storedRows(status.corporationId())).isEqualTo(1);
        assertThat(esi.forgottenCorporations()).containsExactly(corporation);
        assertThat(esi.corporationAssetsAsked()).hasSize(2);
    }

    @Test
    void theNameOfAStructureWithAnOfficeIsLearnedWithTheDirectorsTokenAndIsNotAskedAgain() {
        var viewer = viewerFor(newSub());
        turnOn(viewer);
        var corporation = corporationId();
        var boss = linkWithCorporationScopes(viewer, "The Director");
        esi.inCorporation(boss.eveId(), corporation, "Director");
        esi.corporationNamed(corporation, "Test Industries");
        esi.structure(STRUCTURE, com.eveassist.esi.StructureLookup.found("Our Headquarters", 30000142));
        esi.corporationAssetsOf(corporation, List.of(office(), inOffice(1, 34, 5, "CorpSAG1")));

        fetcher.run(viewer);
        fetcher.run(viewer);

        // asked once, with the Director's token, although the office item (a huge id) is not a structure
        assertThat(esi.structuresAsked()).containsExactly(boss.eveId() + ":" + STRUCTURE);
        assertThat(jdbc.sql("SELECT name FROM character_structure WHERE character_id = :id AND structure_id = :s")
                .param("id", boss.id()).param("s", STRUCTURE).query(String.class).single())
                .isEqualTo("Our Headquarters");
    }

    @Test
    void nothingIsAskedOrShownWhileTheFeatureIsOff() {
        var viewer = viewerFor(newSub());
        var boss = linkWithCorporationScopes(viewer, "The Director");
        esi.inCorporation(boss.eveId(), corporationId(), "Director");

        fetcher.refresh(viewer);

        assertThat(fetcher.enabled(viewer)).isFalse();
        assertThat(fetcher.status(viewer)).isEmpty();
        assertThat(fetcher.anyRunning(viewer)).isFalse();
        assertThat(esi.corporationAssetsAsked()).isEmpty();
        assertThat(corporations.listFor(viewer.accountId().value())).isEmpty();
    }

    @Test
    void aCorporationWithNoDirectorIsListedButItsAssetsAreNotAsked() {
        var viewer = viewerFor(newSub());
        turnOn(viewer);
        var corporation = corporationId();
        var member = linkWithCorporationScopes(viewer, "Just A Member");
        esi.inCorporation(member.eveId(), corporation, "Hangar_Take_1");
        esi.corporationNamed(corporation, "Test Industries");

        fetcher.run(viewer);

        assertThat(only(viewer).phase()).isEqualTo(Phase.NO_DIRECTOR);
        assertThat(esi.corporationAssetsAsked()).isEmpty();
    }

    @Test
    void aDirectorWhoLacksAPermissionCannotBeUsedAndSaysSo() {
        var viewer = viewerFor(newSub());
        turnOn(viewer);
        var corporation = corporationId();
        // it granted the roles scope, so the app can tell it is a Director, but not the assets scope
        var boss = link(viewer, "Half Authorized", "esi-assets.read_assets.v1",
                "esi-characters.read_corporation_roles.v1");
        esi.inCorporation(boss.eveId(), corporation, "Director");
        esi.corporationNamed(corporation, "Test Industries");

        fetcher.run(viewer);

        var status = only(viewer);
        assertThat(status.phase()).isEqualTo(Phase.NEEDS_AUTHORIZATION);
        assertThat(status.directors()).isEqualTo(1);
        assertThat(esi.corporationAssetsAsked()).isEmpty();
    }

    @Test
    void aCharacterWithoutTheRolesPermissionIsNotAskedAboutItsCorporation() {
        var viewer = viewerFor(newSub());
        turnOn(viewer);
        var old = link(viewer, "Old Token", "esi-assets.read_assets.v1");
        esi.inCorporation(old.eveId(), corporationId(), "Director");

        fetcher.run(viewer);

        assertThat(fetcher.status(viewer)).isEmpty();
    }

    @Test
    void onlyTheDirectorsTokenIsUsedWhenOtherCharactersAreInTheSameCorporation() {
        var viewer = viewerFor(newSub());
        turnOn(viewer);
        var corporation = corporationId();
        var member = linkWithCorporationScopes(viewer, "Member");
        var boss = linkWithCorporationScopes(viewer, "Boss");
        esi.inCorporation(member.eveId(), corporation);
        esi.inCorporation(boss.eveId(), corporation, "Director");
        esi.corporationNamed(corporation, "Test Industries");
        esi.corporationAssetsOf(corporation, List.of(office(), inOffice(1, 34, 5, "CorpSAG1")));

        fetcher.run(viewer);

        assertThat(fetcher.status(viewer)).hasSize(1);
        assertThat(esi.corporationAssetsAsked()).containsExactly(corporation + ":" + boss.eveId());
    }

    @Test
    void eachFetchReplacesTheRowsAndAFailureKeepsThemWithItsReason() {
        var viewer = viewerFor(newSub());
        turnOn(viewer);
        var corporation = corporationId();
        var boss = linkWithCorporationScopes(viewer, "Boss");
        esi.inCorporation(boss.eveId(), corporation, "Director");
        esi.corporationNamed(corporation, "Test Industries");
        esi.corporationAssetsOf(corporation, List.of(office(), inOffice(1, 34, 5, "CorpSAG1"),
                inOffice(2, 35, 6, "CorpSAG1")));
        fetcher.run(viewer);
        var id = only(viewer).corporationId();

        esi.corporationAssetsOf(corporation, List.of(office(), inOffice(2, 35, 1, "CorpSAG1")));
        fetcher.run(viewer);
        assertThat(jdbc.sql("SELECT item_id FROM corporation_asset WHERE corporation_id = :id").param("id", id)
                .query(Long.class).list()).containsExactly(2L);

        esi.failCorporationAssets(corporation, "ESI answered 403 for corporations/x/assets");
        fetcher.run(viewer);

        var status = only(viewer);
        assertThat(status.phase()).isEqualTo(Phase.FAILED);
        assertThat(status.problem()).contains("403");
        assertThat(storedRows(id)).isEqualTo(1);
    }

    @Test
    void aStoredCopyThatEsiSaysIsCurrentStaysAsItIs() {
        var viewer = viewerFor(newSub());
        turnOn(viewer);
        var corporation = corporationId();
        var boss = linkWithCorporationScopes(viewer, "Boss");
        esi.inCorporation(boss.eveId(), corporation, "Director");
        esi.corporationNamed(corporation, "Test Industries");
        esi.corporationAssetsOf(corporation, List.of(office(), inOffice(1, 34, 5, "CorpSAG1")));
        fetcher.run(viewer);
        var id = only(viewer).corporationId();

        esi.corporationAssetsUnchanged(corporation);
        fetcher.run(viewer);

        assertThat(storedRows(id)).isEqualTo(1);
        assertThat(only(viewer).phase()).isEqualTo(Phase.CURRENT);
        assertThat(only(viewer).itemCount()).isEqualTo(1);
    }

    @Test
    void aCorporationNoCharacterIsInAnyMoreIsDroppedWithItsAssets() {
        var viewer = viewerFor(newSub());
        turnOn(viewer);
        var corporation = corporationId();
        var boss = linkWithCorporationScopes(viewer, "Boss");
        esi.inCorporation(boss.eveId(), corporation, "Director");
        esi.corporationNamed(corporation, "Old Corp");
        esi.corporationAssetsOf(corporation, List.of(office(), inOffice(1, 34, 5, "CorpSAG1")));
        fetcher.run(viewer);
        var id = only(viewer).corporationId();

        // the character moved: a day later what is known has expired, and ESI now says another corporation
        jdbc.sql("UPDATE character_corporation SET checked_at = :old, corporation_checked_at = :old")
                .param("old", java.sql.Timestamp.from(Instant.now().minusSeconds(90_000))).update();
        var other = corporationId();
        esi.inCorporation(boss.eveId(), other);
        esi.corporationNamed(other, "New Corp");
        fetcher.run(viewer);

        var status = fetcher.status(viewer);
        assertThat(status).extracting(CorporationFetcher.CorporationAssets::name).containsExactly("New Corp");
        assertThat(storedRows(id)).isZero();
    }

    @Test
    void aFailedLookupKeepsWhatWasKnownAndNeverDropsACorporation() {
        var viewer = viewerFor(newSub());
        turnOn(viewer);
        var corporation = corporationId();
        var boss = linkWithCorporationScopes(viewer, "Boss");
        esi.inCorporation(boss.eveId(), corporation, "Director");
        esi.corporationNamed(corporation, "Test Industries");
        esi.corporationAssetsOf(corporation, List.of(office(), inOffice(1, 34, 5, "CorpSAG1")));
        fetcher.run(viewer);
        jdbc.sql("UPDATE character_corporation SET checked_at = :old, corporation_checked_at = :old")
                .param("old", java.sql.Timestamp.from(Instant.now().minusSeconds(7200))).update();
        // ESI no longer answers: not for the character, and not for the corporation's assets
        esi.reset();
        esi.failCorporationAssets(corporation, "ESI could not be reached");

        fetcher.run(viewer);

        assertThat(fetcher.status(viewer)).hasSize(1);
        assertThat(storedRows(only(viewer).corporationId())).isEqualTo(1);
    }

    @Test
    void aCorporationWhoseNameIsNotKnownGetsAPlaceholderAndIsNamedLater() {
        var viewer = viewerFor(newSub());
        turnOn(viewer);
        var corporation = corporationId();
        var boss = linkWithCorporationScopes(viewer, "Boss");
        esi.inCorporation(boss.eveId(), corporation, "Director");

        fetcher.run(viewer);
        assertThat(only(viewer).name()).isEqualTo("Corporation " + corporation);

        esi.corporationNamed(corporation, "Test Industries");
        fetcher.run(viewer);
        assertThat(only(viewer).name()).isEqualTo("Test Industries");
    }

    @Test
    void aSecondAccountNeverSeesOrReadsTheFirstAccountsCorporations() {
        var viewer = viewerFor(newSub());
        var stranger = viewerFor(newSub());
        turnOn(viewer);
        turnOn(stranger);
        var corporation = corporationId();
        var boss = linkWithCorporationScopes(viewer, "Boss");
        esi.inCorporation(boss.eveId(), corporation, "Director");
        esi.corporationNamed(corporation, "Test Industries");
        esi.corporationAssetsOf(corporation, List.of(office(), inOffice(1, 34, 5, "CorpSAG1")));
        fetcher.run(viewer);

        fetcher.run(stranger);

        assertThat(fetcher.status(stranger)).isEmpty();
        assertThat(corporations.listFor(stranger.accountId().value())).isEmpty();
        assertThat(fetcher.status(viewer)).hasSize(1);
    }

    @Test
    void refreshRunsInTheBackgroundAndASecondRequestWhileRunningIsSkipped() {
        var viewer = viewerFor(newSub());
        turnOn(viewer);
        var corporation = corporationId();
        var boss = linkWithCorporationScopes(viewer, "Boss");
        esi.inCorporation(boss.eveId(), corporation, "Director");
        esi.corporationNamed(corporation, "Test Industries");
        esi.corporationAssetsOf(corporation, List.of(office(), inOffice(1, 34, 5, "CorpSAG1")));

        fetcher.refresh(viewer);
        fetcher.refresh(viewer);
        await().atMost(10, TimeUnit.SECONDS).until(() -> !fetcher.anyRunning(viewer));

        assertThat(only(viewer).phase()).isEqualTo(Phase.CURRENT);
    }

    @Test
    void removingTheCharacterRemovesWhatWasLearnedAboutItsCorporation() {
        var viewer = viewerFor(newSub());
        turnOn(viewer);
        var boss = linkWithCorporationScopes(viewer, "Boss");
        esi.inCorporation(boss.eveId(), corporationId(), "Director");
        fetcher.run(viewer);
        assertThat(membershipCount(boss.id())).isEqualTo(1);

        jdbc.sql("DELETE FROM character WHERE public_id = :id").param("id", boss.id()).update();

        assertThat(membershipCount(boss.id())).isZero();
    }

    @Test
    void deletingTheAccountRemovesItsCorporationsAndAssets() {
        var viewer = viewerFor(newSub());
        turnOn(viewer);
        var corporation = corporationId();
        var boss = linkWithCorporationScopes(viewer, "Boss");
        esi.inCorporation(boss.eveId(), corporation, "Director");
        esi.corporationNamed(corporation, "Test Industries");
        esi.corporationAssetsOf(corporation, List.of(office(), inOffice(1, 34, 5, "CorpSAG1")));
        fetcher.run(viewer);
        var id = only(viewer).corporationId();

        jdbc.sql("DELETE FROM account WHERE public_id = :id").param("id", viewer.accountId().value()).update();

        assertThat(storedRows(id)).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM corporation WHERE public_id = :id").param("id", id)
                .query(Integer.class).single()).isZero();
    }

    @Test
    void theCorporationOfACharacterIsAskedOncePerDayAndTheRolesOncePerHour() {
        var viewer = viewerFor(newSub());
        turnOn(viewer);
        var corporation = corporationId();
        var boss = linkWithCorporationScopes(viewer, "Boss");
        esi.inCorporation(boss.eveId(), corporation, "Director");
        esi.corporationNamed(corporation, "Test Industries");
        fetcher.run(viewer);
        var first = membership(boss.id());

        // the roles are two hours old and the corporation is not: only the roles are asked for again
        jdbc.sql("UPDATE character_corporation SET checked_at = :old")
                .param("old", java.sql.Timestamp.from(Instant.now().minusSeconds(7200))).update();
        var other = corporationId();
        esi.inCorporation(boss.eveId(), other);
        fetcher.run(viewer);

        var second = membership(boss.id());
        assertThat(second.eveCorporationId()).isEqualTo(corporation);
        assertThat(second.director()).isFalse();
        assertThat(second.corporationCheckedAt()).isEqualTo(first.corporationCheckedAt());
        assertThat(second.checkedAt()).isAfter(first.checkedAt());

        // a day later the corporation is asked for again, and the character has moved
        jdbc.sql("UPDATE character_corporation SET checked_at = :old, corporation_checked_at = :old")
                .param("old", java.sql.Timestamp.from(Instant.now().minusSeconds(90_000))).update();
        esi.corporationNamed(other, "Other Corp");
        fetcher.run(viewer);
        assertThat(membership(boss.id()).eveCorporationId()).isEqualTo(other);
    }

    @Test
    void storesTheNameOfACorporationContainerAndNeverAsksForTheOffice() {
        var viewer = viewerFor(newSub());
        turnOn(viewer);
        var corporation = corporationId();
        var boss = linkWithCorporationScopes(viewer, "The Director");
        esi.inCorporation(boss.eveId(), corporation, "Director");
        esi.corporationNamed(corporation, "Test Industries");
        esi.corporationAssetsOf(corporation, List.of(office(),
                new CharacterAsset(20, 1001, 1, OFFICE, "item", "CorpSAG2", true, null),
                new CharacterAsset(21, 34, 9, 20, "item", "Unlocked", false, null)));
        esi.itemNamed(20, "Ammo");
        esi.itemNamed(OFFICE, "An office cannot be named");

        fetcher.run(viewer);

        var id = only(viewer).corporationId();
        assertThat(jdbc.sql("SELECT name FROM corporation_asset WHERE corporation_id = :id AND item_id = 20")
                .param("id", id).query(String.class).single()).isEqualTo("Ammo");
        assertThat(esi.namesAsked()).containsExactly(corporation + ":[20]");
    }

    @Test
    void aRunThatRereadsNamesUpdatesACorporationContainerWhenTheListIsUnchanged() {
        var viewer = viewerFor(newSub());
        turnOn(viewer);
        var corporation = corporationId();
        var boss = linkWithCorporationScopes(viewer, "The Director");
        esi.inCorporation(boss.eveId(), corporation, "Director");
        esi.corporationNamed(corporation, "Test Industries");
        esi.corporationAssetsOf(corporation, List.of(office(),
                new CharacterAsset(20, 1001, 1, OFFICE, "item", "CorpSAG2", true, null),
                new CharacterAsset(21, 34, 9, 20, "item", "Unlocked", false, null)));
        esi.itemNamed(20, "Ammo");
        fetcher.run(viewer);
        var id = only(viewer).corporationId();
        var callsBefore = esi.namesAsked().size();

        esi.corporationAssetsUnchanged(corporation);
        esi.itemNamed(20, "Ammo (renamed)");
        fetcher.run(viewer, false);
        assertThat(esi.namesAsked()).hasSize(callsBefore);
        assertThat(nameOfCorporationItem(id, 20)).isEqualTo("Ammo");

        fetcher.run(viewer, true);
        assertThat(nameOfCorporationItem(id, 20)).isEqualTo("Ammo (renamed)");
        // the office was never stored, so it is never asked about
        assertThat(esi.namesAsked()).last().isEqualTo(corporation + ":[20]");
    }

    private String nameOfCorporationItem(UUID corporationId, long itemId) {
        return jdbc.sql("SELECT name FROM corporation_asset WHERE corporation_id = :id AND item_id = :item")
                .param("id", corporationId).param("item", itemId).query(String.class).single();
    }

    @Test
    void anImpoundedOfficeIsNeverAskedForAName() {
        var viewer = viewerFor(newSub());
        turnOn(viewer);
        var corporation = corporationId();
        var boss = linkWithCorporationScopes(viewer, "The Director");
        esi.inCorporation(boss.eveId(), corporation, "Director");
        esi.corporationNamed(corporation, "Test Industries");
        // an office the corporation lost: type Office, flag Impounded, with items still in it. ESI refuses a names call
        // that has it in it.
        esi.corporationAssetsOf(corporation, List.of(
                new CharacterAsset(OFFICE, 27, 1, STATION, "station", "Impounded", true, null),
                new CharacterAsset(30, 34, 5, OFFICE, "item", "CorpSAG1", false, null),
                new CharacterAsset(20, 1001, 1, STATION, "station", "CorpSAG2", true, null),
                new CharacterAsset(21, 34, 9, 20, "item", "Unlocked", false, null)));
        esi.itemNamed(20, "Ammo");

        fetcher.run(viewer);
        esi.corporationAssetsUnchanged(corporation);
        fetcher.run(viewer, true);

        // asked on the first fetch and again on the forced one, each time without the office
        assertThat(esi.namesAsked()).containsExactly(corporation + ":[20]", corporation + ":[20]");
    }
}
