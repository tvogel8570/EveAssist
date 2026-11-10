package com.eveassist.industry.internal.application;

/**
 * What a plan builds of one item: the units it needs, the runs of the blueprint that makes them, the units those runs
 * make (the surplus is what is left over) and how long they take in all.
 */
public record PlanRunLine(
        int typeId, String name, boolean product, long quantity, long runs, long unitsMade, long seconds) {

    public long surplus() {
        return unitsMade - quantity;
    }
}
