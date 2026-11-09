package com.eveassist.esi;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One public contract in a region's list, from {@code GET /contracts/public/{region_id}}. Only the fields EveAssist
 * uses. {@code price} is zero when ESI sends none (courier contracts).
 *
 * @param type            ESI's contract type, for example {@code item_exchange}
 * @param startLocationId where the items are, or null when ESI sends none
 */
public record PublicContract(long contractId, String type, BigDecimal price, long issuerId, Instant dateIssued,
                             Instant dateExpired, Long startLocationId, double volume) {

    public static final String ITEM_EXCHANGE = "item_exchange";

    public boolean isItemExchange() {
        return ITEM_EXCHANGE.equals(type);
    }
}
