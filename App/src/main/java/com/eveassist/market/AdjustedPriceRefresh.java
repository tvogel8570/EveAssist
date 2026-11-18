package com.eveassist.market;

/**
 * What a refresh of the adjusted prices left: how old the stored prices are (null if there are none), and the reason ESI could not be
 * asked or did not answer well (null if it did). A failure keeps the prices that were stored.
 */
public record AdjustedPriceRefresh(AdjustedPriceAge age, String problem) {

    public boolean failed() {
        return problem != null;
    }
}
