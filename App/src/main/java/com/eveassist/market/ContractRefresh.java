package com.eveassist.market;

import java.util.List;

/**
 * What a refresh of a region's public contracts did.
 *
 * @param added      new contracts found in the region's list
 * @param linesRead  contracts whose lines were read this time
 * @param gone       contracts ESI no longer has lines for (expired or accepted), removed
 * @param pending    contracts still waiting for their lines, to be read by the next refresh
 * @param problems   what ESI could not be asked, so the caller can say that some data is old
 */
public record ContractRefresh(int added, int linesRead, int gone, int pending, List<String> problems) implements java.io.Serializable {

    public ContractRefresh {
        problems = List.copyOf(problems);
    }
}
