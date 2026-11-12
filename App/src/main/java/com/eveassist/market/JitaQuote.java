package com.eveassist.market;

import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One item's Jita 4-4 prices. A side with no orders is null: nothing can be bought or sold there.
 *
 * @param sell what it costs to buy the item right away (the cheapest sell orders)
 * @param buy  what it brings to sell the item right away (the highest buy orders)
 * @param asOf when ESI last confirmed these orders were current
 */
public record JitaQuote(TypeId type, BigDecimal sell, BigDecimal buy, Instant asOf) {
}
