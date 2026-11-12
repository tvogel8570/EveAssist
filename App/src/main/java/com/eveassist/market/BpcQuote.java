package com.eveassist.market;

import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * What a blueprint copy of the type costs per run, worked out from public contracts.
 *
 * @param me          the material efficiency asked for
 * @param te          the time efficiency asked for
 * @param perRun      the price of one run
 * @param match       whether the price comes from copies with exactly this ME and TE, or from copies at least as good
 * @param offers      how many contract offers the price is based on
 * @param runsOffered the runs on those offers together
 * @param asOf        when the region's contracts were last confirmed current
 */
public record BpcQuote(TypeId type, int me, int te, BigDecimal perRun, Match match, int offers, long runsOffered,
                       Instant asOf) {

    public enum Match {
        /** Copies with exactly the ME and TE asked for. */
        EXACT,
        /** No copy has exactly that ME and TE, so this uses copies whose ME and TE are both at least as high. */
        AT_LEAST
    }
}
