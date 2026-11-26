package com.eveassist.industry.internal.project.domain;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Which Jita price a project costs at (its price basis): the sell price (an item is bought at once), the buy price (it is bought through buy orders), or
 * the split, the midpoint of the two.
 *
 * <p>The split is the one that the appraisal site Janice shows. Its price for a quantity is the best buy and the best sell price, each times the quantity,
 * and the split is the midpoint of those two totals (checked on 2026-11-25 against three appraisals), so the split of one unit is the midpoint of its buy and sell
 * price and the quantity multiplies that. It needs both sides: an item nobody buys has no buy price, and taking the one side it has, or halving it, would
 * invent a price, so under the split such an item has none and is reported as unpriced.
 */
public enum PriceSide {
    SELL,
    BUY,
    SPLIT;

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    /** The price of one unit at this basis from its Jita sell and buy price, or none when the side or sides it needs are missing. */
    public Optional<BigDecimal> unitPrice(BigDecimal sell, BigDecimal buy) {
        return switch (this) {
            case SELL -> Optional.ofNullable(sell);
            case BUY -> Optional.ofNullable(buy);
            // dividing by two always ends, so nothing is rounded here
            case SPLIT -> sell == null || buy == null ? Optional.empty() : Optional.of(sell.add(buy).divide(TWO));
        };
    }
}
