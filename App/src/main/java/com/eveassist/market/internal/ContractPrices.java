package com.eveassist.market.internal;

import com.eveassist.esi.EsiClient;
import com.eveassist.esi.EsiResult;
import com.eveassist.esi.PublicContractItem;
import com.eveassist.market.BpcQuote;
import com.eveassist.market.ContractRefresh;
import com.eveassist.market.ContractStatus;
import com.eveassist.sde.SdeService;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.stereotype.Service;

/**
 * Reads the public contracts of a region and prices blueprint copies from them. Not transactional: no database
 * connection is held while ESI is being asked.
 */
@Service
public class ContractPrices {

    /** The Forge, the region whose contracts are read. */
    public static final int THE_FORGE = 10000002;

    private final ContractRepository contracts;
    private final OrderRepository orders;
    private final EsiClient esi;
    private final SdeService sde;
    private final Clock clock;

    ContractPrices(ContractRepository contracts, OrderRepository orders, EsiClient esi, SdeService sde, Clock clock) {
        this.sde = sde;
        this.contracts = contracts;
        this.orders = orders;
        this.esi = esi;
        this.clock = clock;
    }

    /**
     * Brings the stored contracts of the region up to date, then reads the lines of up to {@code maxContracts} new
     * contracts, newest first. A region has many thousands, so the first refresh leaves most of them pending and each
     * later one reads more.
     */
    public ContractRefresh refresh(int regionId, int maxContracts) {
        var problems = new ArrayList<String>();
        int added = 0;
        var list = esi.publicContracts(regionId);
        switch (list.outcome()) {
            case UPDATED -> added = contracts.sync(regionId, list.data(), clock.instant());
            case UNCHANGED -> contracts.confirm(regionId, clock.instant());
            case FAILED -> problems.add(list.problem());
        }
        var ids = contracts.pending(regionId, maxContracts);
        var asked = new HashMap<Long, Future<EsiResult<List<PublicContractItem>>>>();
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var id : ids) {
                asked.put(id, threads.submit(() -> esi.publicContractItems(id)));
            }
        }
        int read = 0;
        int gone = 0;
        for (var entry : asked.entrySet()) {
            var result = result(entry.getValue());
            if (result.outcome() == EsiResult.Outcome.FAILED) {
                if (!problems.contains(result.problem())) {
                    problems.add(result.problem());
                }
            } else if (result.data() == null || result.data().isEmpty()) {
                contracts.drop(entry.getKey());
                gone++;
            } else {
                contracts.saveItems(entry.getKey(), result.data(), ContractFlags.of(result.data(), sde::isAbyssal));
                read++;
            }
        }
        return new ContractRefresh(added, read, gone, contracts.pendingCount(regionId), problems);
    }

    /** The price per run of a blueprint copy with this ME and TE. Empty when no stored contract offers one. */
    public Optional<BpcQuote> bpcPrice(int regionId, TypeId blueprint, int me, int te) {
        var now = clock.instant();
        var stored = contracts.contractsWithCopyOf(regionId, blueprint.value(), now);
        var otherTypes = new HashSet<TypeId>();
        stored.forEach(contract -> contract.lines().stream().filter(line -> !line.copy())
                .forEach(line -> otherTypes.add(new TypeId(line.typeId()))));
        var jita = PriceQuoting.quotes(otherTypes, orders);
        var offers = stored.stream()
                .flatMap(contract -> BpcOffers.of(contract, type -> {
                    var quote = jita.get(new TypeId(type));
                    return quote == null ? null : quote.sell();
                }).stream())
                .filter(offer -> offer.typeId() == blueprint.value())
                .toList();
        var asOf = contracts.fetchedAt(regionId).orElse(now);
        return BpcPricing.quote(blueprint, me, te, offers, PriceRule.DEFAULT_SHARE, asOf);
    }

    public ContractStatus status(int regionId) {
        var setAside = contracts.setAside(regionId);
        return new ContractStatus(contracts.fetchedAt(regionId).orElse(null), contracts.pendingCount(regionId), setAside[0],
                setAside[1]);
    }

    private static EsiResult<List<PublicContractItem>> result(Future<EsiResult<List<PublicContractItem>>> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return EsiResult.failed("Interrupted while asking ESI");
        } catch (java.util.concurrent.ExecutionException e) {
            return EsiResult.failed("ESI call failed: " + e.getCause());
        }
    }
}
