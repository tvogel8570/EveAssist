package com.eveassist.industry.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Which Jita price a project's price basis picks. The Jita split is the midpoint of the best buy and the best sell price, as the appraisal site Janice
 * works it out (checked on 2026-11-25 against three appraisals: 2 Vexor at 10,020,000 buy and 10,920,000 sell split at 20,940,000, and 50 Vexor at
 * 523,500,000, so the price of one unit is the midpoint and the quantity multiplies it).
 */
class PriceSideTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    void sellAndBuyPickTheirOwnSideAndNothingWhenThatSideIsMissing() {
        assertThat(PriceSide.SELL.unitPrice(d("10920000"), d("10020000"))).contains(d("10920000"));
        assertThat(PriceSide.BUY.unitPrice(d("10920000"), d("10020000"))).contains(d("10020000"));
        assertThat(PriceSide.SELL.unitPrice(null, d("10020000"))).isEmpty();
        assertThat(PriceSide.BUY.unitPrice(d("10920000"), null)).isEmpty();
        // the other side being missing does not matter to these two
        assertThat(PriceSide.SELL.unitPrice(d("10920000"), null)).contains(d("10920000"));
        assertThat(PriceSide.BUY.unitPrice(null, d("10020000"))).contains(d("10020000"));
    }

    @Test
    void theSplitIsTheMidpointOfTheBuyAndTheSellPriceAndTheQuantityMultipliesIt() {
        var unit = PriceSide.SPLIT.unitPrice(d("10920000"), d("10020000")).orElseThrow();

        assertThat(unit).isEqualByComparingTo("10470000");
        // Janice, appraisal oEBWih (2 Vexor) and f0LcqZ (50 Vexor)
        assertThat(unit.multiply(BigDecimal.valueOf(2))).isEqualByComparingTo("20940000");
        assertThat(unit.multiply(BigDecimal.valueOf(50))).isEqualByComparingTo("523500000");
    }

    @Test
    void theSplitKeepsAHalfCentAndRoundsNothing() {
        // 10.01 and 10.02 are 10.015, which is more precision than ESI's prices have and is not rounded here
        assertThat(PriceSide.SPLIT.unitPrice(d("10.02"), d("10.01")).orElseThrow()).isEqualByComparingTo("10.015");
        assertThat(PriceSide.SPLIT.unitPrice(d("0.03"), d("0.02")).orElseThrow()).isEqualByComparingTo("0.025");
    }

    @Test
    void theSplitNeedsBothSidesAndIsNotGuessedFromOne() {
        // halving a lone side, or taking it as it is, would invent a price, so an item with no buy orders is unpriced under the split
        assertThat(PriceSide.SPLIT.unitPrice(d("10920000"), null)).isEmpty();
        assertThat(PriceSide.SPLIT.unitPrice(null, d("10020000"))).isEmpty();
        assertThat(PriceSide.SPLIT.unitPrice(null, null)).isEmpty();
    }

    @Test
    void aManualPriceThatIsTheSameOnBothSidesIsItselfUnderTheSplit() {
        assertThat(PriceSide.SPLIT.unitPrice(d("5000"), d("5000")).orElseThrow()).isEqualByComparingTo("5000");
    }

    @Test
    void theSplitIsAValueOfTheEnumTheDatabaseAndTheFormNameByItsKey() {
        assertThat(PriceSide.valueOf("SPLIT")).isEqualTo(PriceSide.SPLIT);
        assertThat(PriceSide.values()).containsExactly(PriceSide.SELL, PriceSide.BUY, PriceSide.SPLIT);
    }
}
