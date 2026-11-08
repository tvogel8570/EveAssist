package com.eveassist;

import com.eveassist.esi.EsiClient;
import com.eveassist.esi.EsiResult;
import com.eveassist.esi.MarketOrder;
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
        sells.put(34, new BigDecimal("5.00"));
        sells.put(35, new BigDecimal("10.00"));
    }

    public void sell(int type, String price) {
        sells.put(type, new BigDecimal(price));
    }

    public void fail(int type) {
        broken.add(type);
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
}
