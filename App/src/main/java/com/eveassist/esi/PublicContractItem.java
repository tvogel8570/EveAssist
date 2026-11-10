package com.eveassist.esi;

/**
 * One line of a public contract, from {@code GET /contracts/public/items/{contract_id}}.
 *
 * <p>Only blueprints have fields of their own so far. Abyssal (mutated) items will need more: ESI's line carries an
 * {@code item_id}, which is not read yet, and what makes the item what it is (the mutaplasmid, the source item and the
 * rolled attributes) comes from {@code GET /dogma/dynamic/items/{type_id}/{item_id}}. See "Items with their own
 * attributes" in the market and logistics requirements.
 *
 * @param included        true when the issuer is giving the item, false when the contract asks for it in return
 * @param blueprintCopy   true for a blueprint copy
 * @param materialEfficiency only for blueprints, otherwise null
 * @param timeEfficiency  only for blueprints, otherwise null
 * @param runs            runs left on a copy. The spec says -1 for an original, but ESI leaves {@code runs} out for an
 *                        original (seen 2026-11-09), as it does for anything that is not a blueprint
 */
public record PublicContractItem(long recordId, int typeId, long quantity, boolean included, boolean blueprintCopy,
                                 Integer materialEfficiency, Integer timeEfficiency, Integer runs) {

    /**
     * True for a blueprint original. ESI sends ME and TE for every blueprint, copy or original, and for nothing else,
     * so an original is a line with those levels that is not a copy. Its {@code runs} cannot be used: the spec says -1,
     * and real ESI sends none.
     */
    public boolean blueprintOriginal() {
        return !blueprintCopy && (materialEfficiency != null || timeEfficiency != null);
    }
}
