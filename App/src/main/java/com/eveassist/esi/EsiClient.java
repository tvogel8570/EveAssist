package com.eveassist.esi;

import java.util.List;

/**
 * Every call to ESI goes through here, so that caching, rate limits and the error limit are kept in one place.
 * Feature modules ask for a resource and never build ESI requests themselves.
 */
public interface EsiClient {

    /**
     * The orders for one item in a region, from {@code GET /markets/{region_id}/orders?type_id=...}. When the stored
     * copy is still valid, or ESI answers 304, the result is {@link EsiResult.Outcome#UNCHANGED} and the caller keeps
     * what it stored.
     */
    EsiResult<List<MarketOrder>> marketOrders(int regionId, int typeId);
}
