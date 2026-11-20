package com.eveassist.industry.internal.domain;

import java.math.BigDecimal;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The project's rates and how its quote is worked out.
 *
 * @param iskPerHour what the builder's time is charged at (0 until the developer decides, see the requirements)
 * @param iskPerCubicMeter the flat hauling rate
 * @param contractFee the flat amount that stands in for the contract's cost
 * @param mode which price is the quote, the other is shown as a comparison
 * @param marginPercent the margin on the price, 0 up to below 100
 * @param premiumPercent the premium (or, negative, the discount) on the market price, above -100
 * @param quoteStep the quote is rounded up to a multiple of this, 0 for no rounding
 */
@ValueObject
public record ProjectSettings(
        BigDecimal iskPerHour, BigDecimal iskPerCubicMeter, BigDecimal contractFee, QuoteMode mode,
        BigDecimal marginPercent, BigDecimal premiumPercent, BigDecimal quoteStep) {

    public enum QuoteMode { MARGIN, MARKET }

    public ProjectSettings {
        if (iskPerHour == null || iskPerHour.signum() < 0 || iskPerCubicMeter == null || iskPerCubicMeter.signum() < 0
                || contractFee == null || contractFee.signum() < 0 || quoteStep == null || quoteStep.signum() < 0) {
            throw new IllegalArgumentException("Rates, the fee and the quote step are 0 or more");
        }
        if (mode == null) {
            throw new IllegalArgumentException("A project needs a quote mode");
        }
        if (marginPercent == null || marginPercent.signum() < 0 || marginPercent.compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new IllegalArgumentException("The margin on the price is from 0 to below 100, was " + marginPercent);
        }
        if (premiumPercent == null || premiumPercent.compareTo(BigDecimal.valueOf(-100)) <= 0) {
            throw new IllegalArgumentException("The premium must be above -100, was " + premiumPercent);
        }
    }

    /** No time value, no hauling, the developer's 10,000 ISK contract fee, a 20% margin and a quote to the 1,000 ISK. */
    public static ProjectSettings defaults() {
        return new ProjectSettings(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(10_000), QuoteMode.MARGIN,
                BigDecimal.valueOf(20), BigDecimal.ZERO, BigDecimal.valueOf(1_000));
    }
}
