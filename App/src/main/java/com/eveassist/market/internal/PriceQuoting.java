package com.eveassist.market.internal;

import com.eveassist.market.JitaQuote;
import com.eveassist.sde.TypeId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Works out prices from the stored orders. */
public final class PriceQuoting {

    private PriceQuoting() {
    }

    public static Map<TypeId, JitaQuote> quotes(Collection<TypeId> types, OrderRepository orders) {
        var book = orders.ordersFor(types);
        var asOf = orders.fetchedAt(types);
        return types.stream().filter(asOf::containsKey).collect(Collectors.toMap(type -> type, type -> {
            var sides = book.getOrDefault(type, Map.of());
            return new JitaQuote(type,
                    PriceRule.price(sides.getOrDefault(false, List.of()), false, PriceRule.DEFAULT_SHARE).orElse(null),
                    PriceRule.price(sides.getOrDefault(true, List.of()), true, PriceRule.DEFAULT_SHARE).orElse(null),
                    asOf.get(type));
        }));
    }
}
