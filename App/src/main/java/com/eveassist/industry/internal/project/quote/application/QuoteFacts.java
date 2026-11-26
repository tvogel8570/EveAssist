package com.eveassist.industry.internal.project.quote.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * What a quote says to its buyer: its number, who it is for and the note, when it runs out, the total, and what is quoted, each product with its
 * quantity, its price per unit and its extension (see {@link LinePrices}). Nothing of the cost, the margin or the profit is here, because the
 * shareable text and the CSV never show them. {@code evenly} says that the lines are priced alike because a product had no market price.
 */
public record QuoteFacts(
        int number, String buyer, String note, Instant expiresAt, BigDecimal price, List<Line> lines, boolean evenly) {

    /** A product of the quote: how many, the price of one in whole ISK, and the extension (usually quantity times that price, see {@link LinePrices}). */
    public record Line(String name, long quantity, long unitPrice, long extension) {
    }

    public QuoteFacts {
        lines = List.copyOf(lines);
    }
}
