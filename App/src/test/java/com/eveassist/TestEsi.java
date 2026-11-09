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
        structures.clear();
        structuresAsked.clear();
        assetsGate = null;
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
        return assets.getOrDefault(eveCharacterId, EsiResult.updated(List.of(), Instant.now().plusSeconds(3600)));
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
