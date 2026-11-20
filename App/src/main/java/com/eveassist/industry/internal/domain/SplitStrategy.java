package com.eveassist.industry.internal.domain;

/** Which owned blueprints the splitter uses first. */
public enum SplitStrategy {
    /** The best ME, then TE, and a copy before an original on a tie: the least material, in as few jobs as the limits allow. */
    LEAST_COST,
    /** Every copy first, the one with the most runs first, and then the originals to finish what the copies cannot. */
    COPIES_FIRST
}
