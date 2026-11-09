package com.eveassist.esi;

/**
 * One line of a public contract, from {@code GET /contracts/public/items/{contract_id}}.
 *
 * @param included        true when the issuer is giving the item, false when the contract asks for it in return
 * @param blueprintCopy   true for a blueprint copy
 * @param materialEfficiency only for blueprints, otherwise null
 * @param timeEfficiency  only for blueprints, otherwise null
 * @param runs            runs left on a copy, -1 for an original, null for anything that is not a blueprint
 */
public record PublicContractItem(long recordId, int typeId, long quantity, boolean included, boolean blueprintCopy,
                                 Integer materialEfficiency, Integer timeEfficiency, Integer runs) {
}
