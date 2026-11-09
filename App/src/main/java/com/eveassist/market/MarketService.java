package com.eveassist.market;

import com.eveassist.esi.EsiClient;
import com.eveassist.esi.EsiResult;
import com.eveassist.esi.MarketOrder;
import com.eveassist.market.internal.ContractPrices;
import com.eveassist.market.internal.OrderRepository;
import com.eveassist.market.internal.PriceQuoting;
import com.eveassist.sde.TypeId;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.stereotype.Service;

/** Jita prices. Prices are public data, shared by all users, so these calls take no viewer. */
@Service
public class MarketService {

    /** The Forge, and the station in Jita where the requirements take prices from. */
    static final int THE_FORGE = ContractPrices.THE_FORGE;
    static final long JITA_4_4 = 60003760L;

    private final OrderRepository orders;
    private final EsiClient esi;
    private final Clock clock;
    private final ContractPrices contractPrices;

    MarketService(OrderRepository orders, EsiClient esi, Clock clock, ContractPrices contractPrices) {
        this.contractPrices = contractPrices;
        this.orders = orders;
        this.esi = esi;
        this.clock = clock;
    }

    /**
     * Current Jita prices for the items, asking ESI about every item whose stored orders have expired. The calls run
     * on virtual threads and the ESI client limits how many are made at once. Not transactional: no database
     * connection is held while ESI is being asked.
     */
    public JitaQuotes currentPrices(Collection<TypeId> types) {
        var failed = new HashMap<TypeId, String>();
        var asked = new HashMap<TypeId, Future<EsiResult<List<MarketOrder>>>>();
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var type : types.stream().distinct().toList()) {
                asked.put(type, threads.submit(() -> esi.marketOrders(THE_FORGE, type.value())));
            }
        }
        var now = clock.instant();
        var current = new java.util.ArrayList<TypeId>();
        asked.forEach((type, future) -> {
            var result = result(future);
            switch (result.outcome()) {
                case UPDATED -> {
                    orders.replace(type, result.data().stream().filter(order -> order.locationId() == JITA_4_4).toList(), now);
                    current.add(type);
                }
                case UNCHANGED -> {
                    orders.confirm(type, now);
                    current.add(type);
                }
                case FAILED -> failed.put(type, result.problem());
            }
        });
        return new JitaQuotes(PriceQuoting.quotes(current, orders), failed);
    }

    private static EsiResult<List<MarketOrder>> result(Future<EsiResult<List<MarketOrder>>> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return EsiResult.failed("Interrupted while asking ESI");
        } catch (java.util.concurrent.ExecutionException e) {
            return EsiResult.failed("ESI call failed: " + e.getCause());
        }
    }

    /**
     * Reads The Forge's public contracts: the list, then the lines of up to {@code maxContracts} new contracts. See
     * {@link ContractPrices#refresh}.
     */
    public ContractRefresh refreshContracts(int maxContracts) {
        return contractPrices.refresh(THE_FORGE, maxContracts);
    }

    /**
     * What a blueprint copy with this ME and TE costs per run, from the stored public contracts in The Forge. Prices
     * are public data, shared by all users, so this takes no viewer. Empty when no contract offers one.
     */
    public java.util.Optional<BpcQuote> bpcPrice(TypeId blueprint, int me, int te) {
        return contractPrices.bpcPrice(THE_FORGE, blueprint, me, te);
    }

    /** How current the stored contracts are, for saying so next to a price. */
    public ContractStatus contractStatus() {
        return contractPrices.status(THE_FORGE);
    }
}
