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

    /**
     * All public contracts in a region, from {@code GET /contracts/public/{region_id}}, read through every page. The
     * outcomes are those of {@link #marketOrders}.
     */
    EsiResult<List<PublicContract>> publicContracts(int regionId);

    /**
     * The lines of one public contract, from {@code GET /contracts/public/items/{contract_id}}. A contract's items do
     * not change, so the client does not cache this route: the caller asks once and keeps the answer. The result is
     * {@link EsiResult.Outcome#UPDATED} with no lines when ESI answers 204, which means the contract has expired or
     * was accepted.
     */
    EsiResult<List<PublicContractItem>> publicContractItems(long contractId);
}
