package com.eveassist.industry.internal.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * What a quote says to its buyer: its number, who it is for and the note, when it runs out, the price, and what is quoted (each product with its
 * quantity). Nothing of the cost, the margin or the profit is here, because the shareable text and the CSV never show them.
 */
public record QuoteFacts(int number, String buyer, String note, Instant expiresAt, BigDecimal price, List<Item> items) {

    /** A product of the quote and how many of it. */
    public record Item(String name, long quantity) {
    }

    public QuoteFacts {
        items = List.copyOf(items);
    }
}
