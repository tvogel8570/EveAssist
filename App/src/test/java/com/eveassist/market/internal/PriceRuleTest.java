package com.eveassist.market.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.market.internal.PriceRule.BookOrder;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceRuleTest {

    private static final BigDecimal FIVE_PERCENT = new BigDecimal("0.05");

    private static BookOrder order(String price, long volume) {
        return new BookOrder(new BigDecimal(price), volume);
    }

    @Test
    void averagesTheCheapestSellOrdersUpToTheShareOfTheVolume() {
        // 1,000 units in all, so 5% is 50 units: 30 at 10.00 and 20 at 12.00
        var sells = List.of(order("12.00", 200), order("10.00", 30), order("50.00", 770));

        assertThat(PriceRule.price(sells, false, FIVE_PERCENT)).contains(new BigDecimal("10.80"));
    }

    @Test
    void averagesTheHighestBuyOrdersFirst() {
        // 1,000 units, 5% is 50: 40 at 9.00 and 10 at 8.00
        var buys = List.of(order("8.00", 100), order("9.00", 40), order("1.00", 860));

        assertThat(PriceRule.price(buys, true, FIVE_PERCENT)).contains(new BigDecimal("8.80"));
    }

    @Test
    void usesOnlyTheBestOrderWhenItIsBigEnough() {
        var sells = List.of(order("5.00", 1000), order("6.00", 1000));

        assertThat(PriceRule.price(sells, false, FIVE_PERCENT)).contains(new BigDecimal("5.00"));
    }

    @Test
    void takesPartOfTheLastOrderNeeded() {
        // 5% of 200 is 10 units, all from the first order of 4 and 6 of the second
        var sells = List.of(order("10.00", 4), order("20.00", 196));

        assertThat(PriceRule.price(sells, false, FIVE_PERCENT)).contains(new BigDecimal("16.00"));
    }

    @Test
    void aWholeSideAtShareOneIsTheVolumeWeightedAverageOfAllOrders() {
        var sells = List.of(order("10.00", 1), order("20.00", 3));

        assertThat(PriceRule.price(sells, false, BigDecimal.ONE)).contains(new BigDecimal("17.50"));
    }

    @Test
    void hasNoPriceForASideWithoutVolume() {
        assertThat(PriceRule.price(List.of(), false, FIVE_PERCENT)).isEmpty();
        assertThat(PriceRule.price(List.of(order("5.00", 0)), false, FIVE_PERCENT)).isEmpty();
    }

    @Test
    void oneAbsurdBuyOrderIsAveragedInAndTheShareLimitsItsWeight() {
        var buys = List.of(order("9999999.00", 1), order("9.00", 10_000));

        // 5% of 10,001 is 500.05 units. The absurd order is taken first and counts for 1 of them, so it lifts the price
        assertThat(PriceRule.price(buys, true, FIVE_PERCENT).orElseThrow()).isGreaterThan(new BigDecimal("9.00"));
        assertThat(PriceRule.price(buys, false, FIVE_PERCENT)).contains(new BigDecimal("9.00"));
    }
}
