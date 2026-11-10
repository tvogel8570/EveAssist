package com.eveassist.market.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Turns a contract into a price per run for each blueprint copy in it. Items that are not blueprint copies are taken
 * at their Jita sell price and the rest of the contract price goes to the copies, the same for every run. This is the
 * proportional split from the requirements: copies have no market price, so they share what is left in proportion to
 * their runs.
 */
final class BpcOffers {

    private static final int PRICE_SCALE = 2;

    private BpcOffers() {
    }

    /**
     * Empty when the contract cannot be priced: it asks for items, holds a blueprint original (it has no price per run),
     * holds an item with no Jita price, holds no copy, or is cheaper than its other items.
     *
     * @param jitaSell the Jita sell price of an item type, or null when it has none
     */
    static List<BpcOffer> of(StoredContract contract, IntFunction<BigDecimal> jitaSell) {
        var otherValue = BigDecimal.ZERO;
        var runsByCopy = new LinkedHashMap<CopyKind, Long>();
        long totalRuns = 0;
        for (var line : contract.lines()) {
            if (!line.included()) {
                return List.of();
            }
            if (line.copy()) {
                if (line.runs() == null || line.runs() <= 0) {
                    return List.of();
                }
                var runs = line.quantity() * line.runs();
                runsByCopy.merge(new CopyKind(line.typeId(), line.me() == null ? 0 : line.me(), line.te() == null ? 0 : line.te()), runs, Long::sum);
                totalRuns += runs;
            } else {
                if (line.original()) {
                    return List.of();
                }
                var sell = jitaSell.apply(line.typeId());
                if (sell == null) {
                    return List.of();
                }
                otherValue = otherValue.add(sell.multiply(BigDecimal.valueOf(line.quantity())));
            }
        }
        var copiesValue = contract.price().subtract(otherValue);
        if (totalRuns == 0 || copiesValue.signum() <= 0) {
            return List.of();
        }
        var perRun = copiesValue.divide(BigDecimal.valueOf(totalRuns), PRICE_SCALE, RoundingMode.HALF_UP);
        var offers = new ArrayList<BpcOffer>();
        runsByCopy.forEach((kind, runs) -> offers.add(new BpcOffer(kind.typeId, kind.me, kind.te, perRun, runs)));
        return offers;
    }

    private record CopyKind(int typeId, int me, int te) {
    }
}
