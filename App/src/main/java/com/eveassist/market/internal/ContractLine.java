package com.eveassist.market.internal;

/** One line of a stored public contract. A null {@code runs} means the line is not a blueprint, -1 means an original. */
record ContractLine(int typeId, long quantity, boolean included, boolean copy, int me, int te, Integer runs) {
}
