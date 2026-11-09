package com.eveassist.market;

import com.eveassist.sde.TypeId;
import java.util.Map;

/**
 * The result of asking for current prices. An item ESI could not be asked about is in {@code failed} with the reason,
 * and not in {@code quotes}, so the caller keeps whatever price it already had.
 */
public record JitaQuotes(Map<TypeId, JitaQuote> quotes, Map<TypeId, String> failed) {

    public JitaQuotes {
        quotes = Map.copyOf(quotes);
        failed = Map.copyOf(failed);
    }
}
