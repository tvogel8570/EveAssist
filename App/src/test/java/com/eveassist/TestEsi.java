package com.eveassist;

import com.eveassist.esi.CharacterAsset;
import com.eveassist.esi.EsiClient;
import com.eveassist.esi.EsiResult;
import com.eveassist.esi.MarketOrder;
import com.eveassist.esi.PublicContract;
import com.eveassist.esi.PublicContractItem;
import com.eveassist.esi.StructureLookup;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The ESI that integration tests talk to: one sell order at Jita 4-4 for the items a test gives a price, nothing for
 * the rest, and a failure for items a test breaks. It answers fresh every time, so each call replaces the orders.
 */
public class TestEsi implements EsiClient {

    private static final long JITA_4_4 = 60003760L;

    private final Map<Integer, BigDecimal> sells = new ConcurrentHashMap<>();
    private final Set<Integer> broken = ConcurrentHashMap.newKeySet();
    private final List<Integer> asked = new CopyOnWriteArrayList<>();

    /** Tritanium and Pilot's Mineral have a price, as they did in the shared prices these tests used to rely on. */
    public void reset() {
        sells.clear();
        broken.clear();
        asked.clear();
        contracts.clear();
        contractItems.clear();
        itemsAsked.clear();
        assets.clear();
        assetsAsked.clear();
        staleAssets.clear();
        staleCorporationAssets.clear();
        forgottenCharacters.clear();
        forgottenCorporations.clear();
        structures.clear();
        structuresAsked.clear();
        corporationOf.clear();
        rolesOf.clear();
        corporationNames.clear();
        corporationAssets.clear();
        hangarNames.clear();
        corporationAssetsAsked.clear();
        itemNames.clear();
        namesAsked.clear();
        namesFailure = null;
        assetsGate = null;
        assetsFailure = null;
        sells.put(34, new BigDecimal("5.00"));
        sells.put(35, new BigDecimal("10.00"));
    }

    public void sell(int type, String price) {
        sells.put(type, new BigDecimal(price));
    }

    public void fail(int type) {
        broken.add(type);
    }

    private final Map<Long, EsiResult<List<CharacterAsset>>> assets = new ConcurrentHashMap<>();
    private final List<Long> assetsAsked = new CopyOnWriteArrayList<>();

    /** What ESI answers for a character's assets from now on. A character with no answer set gets an empty list. */
    public void assetsOf(long eveCharacterId, List<CharacterAsset> list) {
        assets.put(eveCharacterId, EsiResult.updated(List.copyOf(list), Instant.now().plusSeconds(3600)));
    }

    /** ESI answers that the stored copy is still current, as it does before the cache time is over. */
    public void assetsUnchanged(long eveCharacterId) {
        assets.put(eveCharacterId, EsiResult.unchanged(Instant.now().plusSeconds(3600)));
    }

    private final Map<Long, List<CharacterAsset>> staleAssets = new ConcurrentHashMap<>();
    private final List<Long> forgottenCharacters = new CopyOnWriteArrayList<>();

    /**
     * ESI answers "unchanged" for the character's assets, as it does for a request that carries an old ETag, until the stored entry
     * is forgotten; then it answers with the list given here. This is the situation after a character is removed and linked again.
     */
    public void assetsUnchangedUntilForgotten(long eveCharacterId, List<CharacterAsset> listAfterForgetting) {
        staleAssets.put(eveCharacterId, List.copyOf(listAfterForgetting));
        assets.put(eveCharacterId, EsiResult.unchanged(Instant.now().plusSeconds(3600)));
    }

    @Override
    public void forgetCharacter(long eveCharacterId) {
        forgottenCharacters.add(eveCharacterId);
        var fresh = staleAssets.remove(eveCharacterId);
        if (fresh != null) {
            assets.put(eveCharacterId, EsiResult.updated(fresh, Instant.now().plusSeconds(3600)));
        }
    }

    /** The characters whose stored ESI entries were forgotten since the test began, in the order forgotten. */
    public List<Long> forgottenCharacters() {
        return List.copyOf(forgottenCharacters);
    }

    public void failAssets(long eveCharacterId, String problem) {
        assets.put(eveCharacterId, EsiResult.failed(problem));
    }

    /** The characters whose assets ESI has been asked about since the test began, in the order asked. */
    public List<Long> assetsAsked() {
        return List.copyOf(assetsAsked);
    }

    private final Map<Long, StructureLookup> structures = new ConcurrentHashMap<>();
    private final List<String> structuresAsked = new CopyOnWriteArrayList<>();

    /** What ESI answers for a structure, whichever character asks. A structure with no answer set is not accessible. */
    public void structure(long structureId, StructureLookup answer) {
        structures.put(structureId, answer);
    }

    /** The asks so far, as {@code "<EVE character id>:<structure id>"}, in the order made. */
    public List<String> structuresAsked() {
        return List.copyOf(structuresAsked);
    }

    @Override
    public StructureLookup structure(long eveCharacterId, long structureId) {
        structuresAsked.add(eveCharacterId + ":" + structureId);
        return structures.getOrDefault(structureId, StructureLookup.noAccess());
    }

    private volatile java.util.concurrent.CountDownLatch assetsGate;
    private volatile RuntimeException assetsFailure;

    /** Makes every asset call throw, as an ESI client does for a failure it did not expect. */
    public void throwOnAssets(RuntimeException failure) {
        assetsFailure = failure;
    }

    /** Holds every asset call until the latch opens, so a test can look at a fetch that is still running. */
    public void holdAssetCalls(java.util.concurrent.CountDownLatch gate) {
        assetsGate = gate;
    }

    @Override
    public EsiResult<List<CharacterAsset>> characterAssets(long eveCharacterId) {
        assetsAsked.add(eveCharacterId);
        var gate = assetsGate;
        if (gate != null) {
            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (assetsFailure != null) {
            throw assetsFailure;
        }
        return assets.getOrDefault(eveCharacterId, EsiResult.updated(List.of(), Instant.now().plusSeconds(3600)));
    }

    private final Map<Long, Long> corporationOf = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> rolesOf = new ConcurrentHashMap<>();
    private final Map<Long, String> corporationNames = new ConcurrentHashMap<>();
    private final Map<Long, EsiResult<List<CharacterAsset>>> corporationAssets = new ConcurrentHashMap<>();
    private final Map<Long, Map<Integer, String>> hangarNames = new ConcurrentHashMap<>();
    private final List<String> corporationAssetsAsked = new CopyOnWriteArrayList<>();

    /** Puts a character in a corporation with the given roles, as ESI says it. A character not put in one is unknown. */
    public void inCorporation(long eveCharacterId, long corporationId, String... roles) {
        corporationOf.put(eveCharacterId, corporationId);
        rolesOf.put(eveCharacterId, Set.of(roles));
    }

    public void corporationNamed(long corporationId, String name) {
        corporationNames.put(corporationId, name);
    }

    /** What ESI answers for a corporation's assets from now on. */
    public void corporationAssetsOf(long corporationId, List<CharacterAsset> list) {
        corporationAssets.put(corporationId, EsiResult.updated(List.copyOf(list), Instant.now().plusSeconds(3600)));
    }

    public void corporationAssetsUnchanged(long corporationId) {
        corporationAssets.put(corporationId, EsiResult.unchanged(Instant.now().plusSeconds(3600)));
    }

    private final Map<Long, List<CharacterAsset>> staleCorporationAssets = new ConcurrentHashMap<>();
    private final List<Long> forgottenCorporations = new CopyOnWriteArrayList<>();

    /** As {@link #assetsUnchangedUntilForgotten}, for a corporation's assets. */
    public void corporationAssetsUnchangedUntilForgotten(long corporationId, List<CharacterAsset> listAfterForgetting) {
        staleCorporationAssets.put(corporationId, List.copyOf(listAfterForgetting));
        corporationAssets.put(corporationId, EsiResult.unchanged(Instant.now().plusSeconds(3600)));
    }

    @Override
    public void forgetCorporation(long eveCorporationId) {
        forgottenCorporations.add(eveCorporationId);
        var fresh = staleCorporationAssets.remove(eveCorporationId);
        if (fresh != null) {
            corporationAssets.put(eveCorporationId, EsiResult.updated(fresh, Instant.now().plusSeconds(3600)));
        }
    }

    public List<Long> forgottenCorporations() {
        return List.copyOf(forgottenCorporations);
    }

    public void failCorporationAssets(long corporationId, String problem) {
        corporationAssets.put(corporationId, EsiResult.failed(problem));
    }

    public void hangars(long corporationId, Map<Integer, String> names) {
        hangarNames.put(corporationId, Map.copyOf(names));
    }

    /** The corporation asset calls so far, as {@code "<EVE corporation id>:<EVE character id of the token>"}. */
    public List<String> corporationAssetsAsked() {
        return List.copyOf(corporationAssetsAsked);
    }

    @Override
    public EsiResult<Long> characterCorporationId(long eveCharacterId) {
        var corporation = corporationOf.get(eveCharacterId);
        return corporation == null ? EsiResult.failed("test ESI does not know the character")
                : EsiResult.updated(corporation, Instant.now().plusSeconds(86400));
    }

    @Override
    public EsiResult<Set<String>> characterRoles(long eveCharacterId) {
        var roles = rolesOf.get(eveCharacterId);
        return roles == null ? EsiResult.failed("test ESI does not know the character")
                : EsiResult.updated(roles, Instant.now().plusSeconds(3600));
    }

    @Override
    public EsiResult<String> corporationName(long eveCorporationId) {
        var name = corporationNames.get(eveCorporationId);
        return name == null ? EsiResult.failed("test ESI does not know the corporation")
                : EsiResult.updated(name, Instant.now().plusSeconds(3600));
    }

    @Override
    public EsiResult<List<CharacterAsset>> corporationAssets(long eveCorporationId, long viaEveCharacterId) {
        corporationAssetsAsked.add(eveCorporationId + ":" + viaEveCharacterId);
        return corporationAssets.getOrDefault(eveCorporationId,
                EsiResult.updated(List.of(), Instant.now().plusSeconds(3600)));
    }

    @Override
    public EsiResult<Map<Integer, String>> corporationHangars(long eveCorporationId, long viaEveCharacterId) {
        return EsiResult.updated(hangarNames.getOrDefault(eveCorporationId, Map.of()), Instant.now().plusSeconds(3600));
    }

    private final Map<Long, String> itemNames = new ConcurrentHashMap<>();
    private final List<String> namesAsked = new CopyOnWriteArrayList<>();
    private volatile String namesFailure;

    /** Gives an item a name, as a player does to a container or ship. Any item id, whoever holds it. */
    public void itemNamed(long itemId, String name) {
        itemNames.put(itemId, name);
    }

    /** Takes a name away again, as a player does when they clear it: ESI then gives no name for the item. */
    public void itemUnnamed(long itemId) {
        itemNames.remove(itemId);
    }

    /** Makes every names call fail with this problem. */
    public void failNames(String problem) {
        namesFailure = problem;
    }

    /** The names calls so far, as {@code "<EVE character or corporation id>:<item ids>"}, in the order made. */
    public List<String> namesAsked() {
        return List.copyOf(namesAsked);
    }

    private EsiResult<Map<Long, String>> namesOf(long owner, List<Long> itemIds) {
        namesAsked.add(owner + ":" + itemIds);
        if (namesFailure != null) {
            return EsiResult.failed(namesFailure);
        }
        var found = new java.util.LinkedHashMap<Long, String>();
        itemIds.forEach(id -> {
            if (itemNames.containsKey(id)) {
                found.put(id, itemNames.get(id));
            }
        });
        return EsiResult.updated(found, Instant.now());
    }

    @Override
    public EsiResult<Map<Long, String>> characterAssetNames(long eveCharacterId, List<Long> itemIds) {
        return namesOf(eveCharacterId, itemIds);
    }

    @Override
    public EsiResult<Map<Long, String>> corporationAssetNames(long eveCorporationId, long viaEveCharacterId,
            List<Long> itemIds) {
        return namesOf(eveCorporationId, itemIds);
    }

    /** The items ESI has been asked about since the test began, in the order asked. */
    public List<Integer> asked() {
        return List.copyOf(asked);
    }

    @Override
    public EsiResult<List<MarketOrder>> marketOrders(int regionId, int typeId) {
        asked.add(typeId);
        if (broken.contains(typeId)) {
            return EsiResult.failed("test ESI is down for " + typeId);
        }
        var price = sells.get(typeId);
        var orders = price == null
                ? List.<MarketOrder>of()
                : List.of(new MarketOrder(typeId * 10L, typeId, JITA_4_4, false, price, 1000));
        return EsiResult.updated(orders, Instant.now().plusSeconds(300));
    }

    private final List<PublicContract> contracts = new CopyOnWriteArrayList<>();
    private final Map<Long, List<PublicContractItem>> contractItems = new ConcurrentHashMap<>();
    private final List<Long> itemsAsked = new CopyOnWriteArrayList<>();

    /** Puts a public contract in the region's list, with the lines ESI will give for it. */
    public void contract(PublicContract contract, List<PublicContractItem> items) {
        contracts.add(contract);
        contractItems.put(contract.contractId(), items);
    }

    /** The contracts whose lines ESI has been asked for since the test began, in the order asked. */
    public List<Long> contractItemsAsked() {
        return List.copyOf(itemsAsked);
    }

    @Override
    public EsiResult<List<PublicContract>> publicContracts(int regionId) {
        return EsiResult.updated(List.copyOf(contracts), Instant.now().plusSeconds(1800));
    }

    @Override
    public EsiResult<List<PublicContractItem>> publicContractItems(long contractId) {
        itemsAsked.add(contractId);
        return EsiResult.updated(contractItems.getOrDefault(contractId, List.of()), Instant.now().plusSeconds(3600));
    }
}
