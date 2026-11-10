package com.eveassist.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.esi.CharacterAsset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssetClassifierTest {

    private static final long STATION = 60003760L;
    private static final long STRUCTURE = 1_023_603_822_720L;

    private static CharacterAsset item(long itemId, int typeId, long locationId, String locationType, String flag) {
        return new CharacterAsset(itemId, typeId, 1, locationId, locationType, flag, false, null);
    }

    private static AssetClassifier.Placed placed(List<CharacterAsset> assets, long itemId) {
        return AssetClassifier.classify(assets).stream().filter(p -> p.asset().itemId() == itemId).findFirst()
                .orElseThrow();
    }

    @Test
    void anItemAtTheTopIsInItsStationInAHangar() {
        var result = placed(List.of(item(1, 34, STATION, "station", "Hangar")), 1);

        assertThat(result.rootLocationId()).isEqualTo(STATION);
        assertThat(result.depth()).isZero();
        assertThat(result.containerTypeId()).isNull();
        assertThat(result.held()).isEqualTo(Held.HANGAR);
    }

    @Test
    void anItemInAContainerInAShipInAStationIsFoundAtTheStation() {
        var assets = List.of(
                item(10, 587, STATION, "station", "Hangar"),
                item(11, 3296, 10, "item", "Cargo"),
                item(12, 34, 11, "item", "Unlocked"));

        var deep = placed(assets, 12);

        assertThat(deep.rootLocationId()).isEqualTo(STATION);
        assertThat(deep.depth()).isEqualTo(2);
        assertThat(deep.containerTypeId()).isEqualTo(3296);
        assertThat(deep.held()).isEqualTo(Held.IN_CONTAINER);
        assertThat(placed(assets, 11).held()).isEqualTo(Held.IN_SHIP);
        assertThat(placed(assets, 11).containerTypeId()).isEqualTo(587);
    }

    @Test
    void anItemInAStructureIsAtTheStructureWhateverTheLocationTypeSays() {
        // real ESI types an item in a structure as "item", and the id is not an item of this character
        var assets = List.of(
                item(1, 34, STRUCTURE, "item", "Hangar"),
                item(2, 3296, STRUCTURE, "other", "Hangar"),
                item(3, 34, 2, "item", "Unlocked"));

        assertThat(placed(assets, 1).rootLocationId()).isEqualTo(STRUCTURE);
        assertThat(placed(assets, 1).depth()).isZero();
        assertThat(placed(assets, 2).rootLocationId()).isEqualTo(STRUCTURE);
        assertThat(placed(assets, 3).rootLocationId()).isEqualTo(STRUCTURE);
        assertThat(placed(assets, 3).depth()).isEqualTo(1);
    }

    @Test
    void aFittedModuleIsFittedAndNotLoose() {
        var assets = List.of(
                item(10, 587, STATION, "station", "Hangar"),
                item(11, 2048, 10, "item", "HiSlot0"),
                item(12, 3636, 10, "item", "MedSlot3"),
                item(13, 31, 10, "item", "RigSlot1"),
                item(14, 1, 10, "item", "SubSystemSlot0"),
                item(15, 2, 10, "item", "ServiceSlot0"));

        for (long id = 11; id <= 15; id++) {
            assertThat(placed(assets, id).held()).as("item %d", id).isEqualTo(Held.FITTED);
        }
    }

    @Test
    void shipHoldsCountAsInAShip() {
        var assets = List.of(
                item(10, 587, STATION, "station", "Hangar"),
                item(11, 1, 10, "item", "Cargo"),
                item(12, 2, 10, "item", "DroneBay"),
                item(13, 3, 10, "item", "FighterBay"),
                item(14, 4, 10, "item", "FleetHangar"),
                item(15, 5, 10, "item", "SpecializedOreHold"),
                item(16, 6, 10, "item", "FighterTube2"));

        for (long id = 11; id <= 16; id++) {
            assertThat(placed(assets, id).held()).as("item %d", id).isEqualTo(Held.IN_SHIP);
        }
    }

    @Test
    void assetSafetyAndDeliveriesAreDecidedByTheFlagAtAnyDepth() {
        var assets = List.of(
                item(1, 34, STATION, "station", "AssetSafety"),
                item(2, 60, STATION, "station", "Deliveries"),
                item(3, 60, 1, "item", "AssetSafety"),
                item(4, 60, STATION, "station", "Hangar"));

        assertThat(placed(assets, 1).held()).isEqualTo(Held.ASSET_SAFETY);
        assertThat(placed(assets, 2).held()).isEqualTo(Held.DELIVERIES);
        // inside an asset safety wrap, still asset safety
        assertThat(placed(assets, 3).held()).isEqualTo(Held.ASSET_SAFETY);
        assertThat(placed(assets, 4).held()).isEqualTo(Held.HANGAR);
    }

    @Test
    void anItemInSpaceIsAtItsSolarSystem() {
        var result = placed(List.of(item(1, 587, 30000142L, "solar_system", "Unlocked")), 1);

        assertThat(result.rootLocationId()).isEqualTo(30000142L);
        assertThat(result.held()).isEqualTo(Held.HANGAR);
    }

    @Test
    void aChainThatLoopsStopsInsteadOfSpinning() {
        var assets = List.of(item(1, 34, 2, "item", "Unlocked"), item(2, 34, 1, "item", "Unlocked"));

        var result = placed(assets, 1);

        assertThat(result.depth()).isEqualTo(64);
    }

    @Test
    void aLongChainThatEndsAtAPlaceIsFollowedAllTheWay() {
        var assets = new ArrayList<CharacterAsset>();
        assets.add(item(1, 34, STATION, "station", "Hangar"));
        for (long id = 2; id <= 20; id++) {
            assets.add(item(id, 3296, id - 1, "item", "Unlocked"));
        }

        var result = placed(assets, 20);

        assertThat(result.rootLocationId()).isEqualTo(STATION);
        assertThat(result.depth()).isEqualTo(19);
    }

    // ---- a corporation's assets: an office is a folder, and its flag names the hangar division

    private static final long OFFICE = 1_040_000_000_001L;

    private static List<AssetClassifier.Placed> corporation(List<CharacterAsset> assets) {
        return AssetClassifier.classifyCorporation(assets);
    }

    private static AssetClassifier.Placed corp(List<CharacterAsset> assets, long itemId) {
        return corporation(assets).stream().filter(p -> p.asset().itemId() == itemId).findFirst().orElseThrow();
    }

    @Test
    void anOfficeIsNotAnItemAndItsContentsAreAtTheStationInAHangarDivision() {
        var assets = List.of(
                item(OFFICE, 27, STRUCTURE, "item", "OfficeFolder"),
                item(1, 34, OFFICE, "item", "CorpSAG3"),
                item(2, 35, OFFICE, "item", "CorpSAG7"));

        var result = corporation(assets);

        assertThat(result).extracting(p -> p.asset().itemId()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(corp(assets, 1).rootLocationId()).isEqualTo(STRUCTURE);
        assertThat(corp(assets, 1).depth()).isZero();
        assertThat(corp(assets, 1).containerTypeId()).isNull();
        assertThat(corp(assets, 1).held()).isEqualTo(Held.HANGAR);
        assertThat(corp(assets, 1).division()).isEqualTo(3);
        assertThat(corp(assets, 2).division()).isEqualTo(7);
    }

    @Test
    void anItemInAContainerInAnOfficeTakesTheDivisionOfTheContainer() {
        var assets = List.of(
                item(OFFICE, 27, STATION, "station", "OfficeFolder"),
                item(10, 3296, OFFICE, "item", "CorpSAG2"),
                item(11, 34, 10, "item", "Unlocked"),
                item(12, 587, OFFICE, "item", "CorpSAG2"),
                item(13, 2048, 12, "item", "HiSlot0"));

        assertThat(corp(assets, 11).division()).isEqualTo(2);
        assertThat(corp(assets, 11).depth()).isEqualTo(1);
        assertThat(corp(assets, 11).containerTypeId()).isEqualTo(3296);
        assertThat(corp(assets, 11).held()).isEqualTo(Held.IN_CONTAINER);
        assertThat(corp(assets, 13).held()).isEqualTo(Held.FITTED);
        assertThat(corp(assets, 13).division()).isEqualTo(2);
        assertThat(corp(assets, 13).rootLocationId()).isEqualTo(STATION);
    }

    @Test
    void aCorporationItemOutsideAnOfficeHasNoDivision() {
        var assets = List.of(item(1, 34, 30000142L, "solar_system", "AutoFit"));

        assertThat(corp(assets, 1).division()).isNull();
        assertThat(corp(assets, 1).rootLocationId()).isEqualTo(30000142L);
    }

    @Test
    void aCharactersOfficeFolderFlagIsNotSpecialAndNoDivisionIsGiven() {
        var assets = List.of(item(1, 34, STATION, "station", "Hangar"));

        assertThat(placed(assets, 1).division()).isNull();
    }

    @Test
    void divisionsAreOneToSevenOnly() {
        assertThat(AssetClassifier.divisionOf("CorpSAG1")).isEqualTo(1);
        assertThat(AssetClassifier.divisionOf("CorpSAG7")).isEqualTo(7);
        assertThat(AssetClassifier.divisionOf("CorpSAG0")).isNull();
        assertThat(AssetClassifier.divisionOf("CorpSAG8")).isNull();
        assertThat(AssetClassifier.divisionOf("CorpDeliveries")).isNull();
        assertThat(AssetClassifier.divisionOf("Hangar")).isNull();
        assertThat(AssetClassifier.divisionOf(null)).isNull();
    }
}
