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
        /**
         * A job's material has no adjusted price in ESI, and the type is the material. A notice: the game counts it as 0 in the estimated item
         * value as this costing does, so the install cost is right (reading G16, 2026-11-25) and it only tells the user why the material adds nothing.
         */
        MISSING_ADJUSTED_PRICE,
        /**
         * A product has no price at the project's basis, in a market-referenced quote, which is the products' Jita prices, so there is no quote. A margin quote does
         * not use them, so it is not raised there. The type is the product, and the way out is a price the user types for it (decided with the developer on
         * 2026-11-25), or the margin quote. It blocks the quote, like any other gap.
         */
        NO_MARKET_PRICE,
        /** An item has no volume while hauling is charged, so the hauling is too low. */
        MISSING_VOLUME,
        /** The jobs' runs make fewer units than the project needs. */
        TOO_FEW_RUNS,
        /** An item is built where no facility has been chosen, so its install cost is left out and the cost is too low. */
        NO_FACILITY,
        /** A job uses a blueprint original, which is always available and so is easy to use without meaning to. A notice. */
        USES_ORIGINAL,
        /** One run already takes longer than the time bound, so a job is over it. A notice: the bound cannot be met. */
        TIME_BOUND_NOT_MET;

        public boolean blocksQuote() {
            return this != USES_ORIGINAL && this != TIME_BOUND_NOT_MET && this != MISSING_ADJUSTED_PRICE;
        }
    }
}
