package com.eveassist.industry.internal.application;

/**
 * The stored costing has a warning that blocks a quote (an unpriced item, a missing adjusted price, an item with no facility, or too few
 * runs), or no price. A frozen quote never hides a gap.
 */
public class NotQuotableException extends RuntimeException {
}
