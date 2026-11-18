package com.eveassist.market;

import java.time.Instant;

/**
 * What a refresh of the adjusted prices left: when the stored prices were last known to be ESI's current copy (null if never), and
 * the reason ESI could not be asked or did not answer well (null if it did). A failure keeps the prices that were stored.
 */
public record AdjustedPriceRefresh(Instant asOf, String problem) {

    public boolean failed() {
        return problem != null;
    }
}
