package com.eveassist.esi;

import java.math.BigDecimal;

/**
 * CCP's price index for one item type, from {@code GET /markets/prices}. Only the type is certain: ESI's spec lists no other field
 * as required, so either price may be missing. An adjusted price of 0 is a real value (about half of today's rows are 0), and the
 * game's estimated item value uses these figures as they are.
 *
 * @param adjustedPrice the price an estimated item value is worked out from, or null if ESI sent none
 * @param averagePrice the average price over a period, or null
 */
public record MarketPrice(int typeId, BigDecimal adjustedPrice, BigDecimal averagePrice) {
}
