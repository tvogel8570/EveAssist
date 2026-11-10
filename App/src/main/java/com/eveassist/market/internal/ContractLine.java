package com.eveassist.market.internal;

/**
 * One line of a stored public contract. ME, TE and runs are null for anything that is not a blueprint (and runs is also
 * null for an original, which ESI sends without it).
 */
record ContractLine(int typeId, long quantity, boolean included, boolean copy, Integer me, Integer te, Integer runs) {

    /** A blueprint original: not a copy, with ME or TE levels. See {@code PublicContractItem#blueprintOriginal()}. */
    boolean original() {
        return !copy && (me != null || te != null);
    }
}
