package com.eveassist.industry.internal.costing.domain;

import java.math.BigDecimal;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * How a job's time came about, in the shape of the game's duration hover: the blueprint's TE, then "Skills and Implants",
 * then the structure's role bonus. Each factor is the share of the time that is left (0.80 means 20% saved), and they all
 * multiply.
 *
 * @param blueprintFactor the blueprint's TE
 * @param skillsAndImplantsFactor Industry, Advanced Industry, the blueprint's time skills and the implant
 * @param structureFactor the structure's time bonus
 * @param rigFactor the rigs' time bonus, already scaled for security
 * @param exactSeconds the time before rounding
 * @param seconds the time rounded to the nearest second, which the window shows
 * @param maximumRuns the most runs one job of this blueprint may have, the window's "Maximum runs"
 */
@ValueObject
public record TimeBreakdown(
        long baseSecondsPerRun, long runs, BigDecimal blueprintFactor, BigDecimal skillsAndImplantsFactor,
        BigDecimal structureFactor, BigDecimal rigFactor, BigDecimal exactSeconds, long seconds, long maximumRuns) {

    /** The percent a factor saves, as the hover shows it: 0.68 is 32.0. */
    public static BigDecimal savedPercent(BigDecimal factor) {
        return BigDecimal.ONE.subtract(factor).movePointRight(2);
    }
}
