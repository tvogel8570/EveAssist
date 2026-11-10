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

    /**
     * Everything a character owns, from {@code GET /characters/{character_id}/assets}, read through every page. It
     * needs the scope {@value #ASSETS_SCOPE}, and is asked with the character's access token (see {@link AccessTokens}).
     * The outcomes are those of {@link #marketOrders}. A character that needs re-authorization or lacks the scope is a
     * {@link EsiResult.Outcome#FAILED} result whose problem says so, and ESI is not called.
     */
    EsiResult<List<CharacterAsset>> characterAssets(long eveCharacterId);

    String ASSETS_SCOPE = "esi-assets.read_assets.v1";

    /**
     * The name of a player structure, from {@code GET /universe/structures/{structure_id}}, asked with the
     * character's token. It needs the scope {@value #STRUCTURES_SCOPE}. ESI answers only for a character that may dock
     * there, and answers 403 to the rest, which counts toward its error limit, so callers ask about as few structures
     * as they can. The answer is not cached here: the caller keeps it, as it keeps its other stored data.
     */
    StructureLookup structure(long eveCharacterId, long structureId);

    String STRUCTURES_SCOPE = "esi-universe.read_structures.v1";

    /**
     * The id of the corporation a character belongs to, from the public {@code GET /characters/{character_id}}. It
     * needs no token. The answer is not cached here: the caller keeps it and asks again only after a while.
     */
    EsiResult<Long> characterCorporationId(long eveCharacterId);

    /**
     * The in-game corporation roles of a character, from {@code GET /characters/{character_id}/roles}, asked with the
     * character's token. It needs the scope {@value #ROLES_SCOPE}. The answer is not cached here, as for
     * {@link #characterCorporationId}.
     */
    EsiResult<java.util.Set<String>> characterRoles(long eveCharacterId);

    String ROLES_SCOPE = "esi-characters.read_corporation_roles.v1";

    /** The name of a corporation, from the public {@code GET /corporations/{corporation_id}}. Not cached here. */
    EsiResult<String> corporationName(long eveCorporationId);

    /**
     * Everything a corporation owns, from {@code GET /corporations/{corporation_id}/assets}, read through every page,
     * asked with the token of a character that has the Director role there. It needs the scope
     * {@value #CORPORATION_ASSETS_SCOPE}. The items have the same fields as a character's, and the outcomes are those
     * of {@link #characterAssets}. ESI answers 403 when the character has lost the role, which is a
     * {@link EsiResult.Outcome#FAILED} result. The stored copy is kept per corporation, not per account.
     */
    EsiResult<List<CharacterAsset>> corporationAssets(long eveCorporationId, long viaEveCharacterId);

    String CORPORATION_ASSETS_SCOPE = "esi-assets.read_corporation_assets.v1";

    /**
     * The names a corporation gave its seven hangar divisions, by division number (1 to 7), from
     * {@code GET /corporations/{corporation_id}/divisions}, asked with a Director's token. It needs the scope
     * {@value #DIVISIONS_SCOPE}. Not cached here.
     */
    EsiResult<java.util.Map<Integer, String>> corporationHangars(long eveCorporationId, long viaEveCharacterId);

    String DIVISIONS_SCOPE = "esi-corporations.read_divisions.v1";
}
