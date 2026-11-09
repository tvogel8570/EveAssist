package com.eveassist.market.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The price rule from the requirements: the volume-weighted average of the best orders that make up a share of one
 * side's volume (the cheapest sell orders, or the highest buy orders). The same price is used whatever the quantity.
 */
final class PriceRule {

    /** The default share. It becomes a user setting later. */
    static final BigDecimal DEFAULT_SHARE = new BigDecimal("0.05");

    record BookOrder(BigDecimal price, long volume) {
    }

    private PriceRule() {
    }

    /** Empty when the side has no volume. A share of 1 gives the average of the whole side. */
    static Optional<BigDecimal> price(List<BookOrder> side, boolean highestFirst, BigDecimal share) {
        var total = side.stream().mapToLong(BookOrder::volume).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        var target = BigDecimal.valueOf(total).multiply(share);
        var best = side.stream()
                .sorted(highestFirst
                        ? Comparator.comparing(BookOrder::price).reversed()
                        : Comparator.comparing(BookOrder::price))
                .toList();
        var taken = BigDecimal.ZERO;
        var worth = BigDecimal.ZERO;
        for (var order : best) {
            var wanted = target.subtract(taken);
            if (wanted.signum() <= 0) {
                break;
            }
            var units = wanted.min(BigDecimal.valueOf(order.volume()));
            taken = taken.add(units);
            worth = worth.add(order.price().multiply(units));
        }
        return Optional.of(worth.divide(taken, 2, RoundingMode.HALF_UP));
    }
}
