package com.eveassist.market.internal;

import java.math.BigDecimal;

/** What one contract asks for runs of one blueprint copy, per run. */
record BpcOffer(int typeId, int me, int te, BigDecimal perRun, long runs) {
}
