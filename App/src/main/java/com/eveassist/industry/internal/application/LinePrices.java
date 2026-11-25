package com.eveassist.industry.internal.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Shares a quote's total out as a price for each of its lines (decided with the developer on 2026-11-25), because the quote is one total and the sheet a
 * buyer gets has a unit price and an extension for each product. The shares follow the products' market value (the Jita price at the project's price basis times
 * the quantity), since the app has no cost for a single product: parts are shared between them. If any product has no price, every unit gets the same price instead.
 *
 * <p>A unit price is whole ISK. The leftover, which is only a few ISK, goes into the extension of the biggest line, so that the lines add up to the
 * total exactly. That row may then not equal its quantity times its price to the ISK, which is the developer's choice over an extra "Rounding" line.
 */
final class LinePrices {

    /** A product of the quote with its quantity and its market price per unit (null when it has none). */
    record Product(String name, long quantity, BigDecimal marketPrice) {
    }

    /** The lines, and whether they had to be priced alike because a product had no market price. */
    record Result(List<QuoteFacts.Line> lines, boolean evenly) {
    }

    private LinePrices() {
    }

    static Result of(BigDecimal total, List<Product> products) {
        if (products.isEmpty()) {
            return new Result(List.of(), false);
        }
        var whole = total.setScale(0, RoundingMode.HALF_UP);
        var priced = products.stream().allMatch(product -> product.marketPrice() != null && product.marketPrice().signum() > 0);
        var weights = products.stream()
                .map(product -> BigDecimal.valueOf(product.quantity()).multiply(priced ? product.marketPrice() : BigDecimal.ONE)).toList();
        var weightSum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        var unitPrices = new ArrayList<Long>();
        var extensions = new ArrayList<Long>();
        long sum = 0;
        for (int i = 0; i < products.size(); i++) {
            var quantity = BigDecimal.valueOf(products.get(i).quantity());
            // this line's share of the total, per unit, to the whole ISK
            var unit = whole.multiply(weights.get(i)).divide(weightSum.multiply(quantity), 0, RoundingMode.HALF_UP).longValueExact();
            unitPrices.add(unit);
            extensions.add(unit * products.get(i).quantity());
            sum += unit * products.get(i).quantity();
        }
        var biggest = 0;
        for (int i = 1; i < extensions.size(); i++) {
            if (extensions.get(i) > extensions.get(biggest)) {
                biggest = i;
            }
        }
        extensions.set(biggest, extensions.get(biggest) + (whole.longValueExact() - sum));

        var lines = new ArrayList<QuoteFacts.Line>();
        for (int i = 0; i < products.size(); i++) {
            lines.add(new QuoteFacts.Line(products.get(i).name(), products.get(i).quantity(), unitPrices.get(i), extensions.get(i)));
        }
        return new Result(lines, !priced);
    }
}
