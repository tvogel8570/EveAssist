package com.eveassist.industry.internal.domain;

import com.eveassist.sde.TypeId;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Something the page should say. Every kind that makes the cost too low or wrong blocks issuing a quote (the developer
 * decided on 2026-11-18 that a frozen quote never hides a gap). A notice only tells, and does not block.
 *
 * @param type the item the warning is about
 */
@ValueObject
public record ProjectWarning(Kind kind, TypeId type) {

    public enum Kind {
        /** A bought item has no price, so the materials are too low. */
        UNPRICED_MATERIAL,
        /** A built item's surplus has no price, so the credit is too low and the cost too high. */
        UNPRICED_SURPLUS,
        /** A job's material has no adjusted price, so its estimated item value and install cost are too low. */
        MISSING_ADJUSTED_PRICE,
        /** An item has no volume while hauling is charged, so the hauling is too low. */
        MISSING_VOLUME,
        /** The jobs' runs make fewer units than the project needs. */
        TOO_FEW_RUNS,
        /** A job uses a blueprint original, which is always available and so is easy to use without meaning to. A notice. */
        USES_ORIGINAL,
        /** One run already takes longer than the time bound, so a job is over it. A notice: the bound cannot be met. */
        TIME_BOUND_NOT_MET;

        public boolean blocksQuote() {
            return this != USES_ORIGINAL && this != TIME_BOUND_NOT_MET;
        }
    }
}
