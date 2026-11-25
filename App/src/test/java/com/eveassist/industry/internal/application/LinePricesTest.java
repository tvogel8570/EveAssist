package com.eveassist.industry.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** How a quote's total is shared out as a price for each line. Every case is worked out by hand from the rule the developer chose on 2026-11-25. */
class LinePricesTest {

    private static LinePrices.Product product(String name, long quantity, String price) {
        return new LinePrices.Product(name, quantity, price == null ? null : new BigDecimal(price));
    }

    private static List<Long> units(LinePrices.Result result) {
        return result.lines().stream().map(QuoteFacts.Line::unitPrice).toList();
    }

    private static List<Long> extensions(LinePrices.Result result) {
        return result.lines().stream().map(QuoteFacts.Line::extension).toList();
    }

    @Test
    void theTotalIsSharedByMarketValueSoDifferentShipsGetDifferentPrices() {
        // a Vexor worth 4M and 2 Legions worth 6M each: the values are 4M and 12M, a quarter and three quarters of 16M
        var result = LinePrices.of(new BigDecimal("16000000"), List.of(product("Vexor", 1, "4000000"), product("Legion", 2, "6000000")));

        assertThat(units(result)).containsExactly(4_000_000L, 6_000_000L);
        assertThat(extensions(result)).containsExactly(4_000_000L, 12_000_000L);
        assertThat(result.evenly()).isFalse();
    }

    @Test
    void theSheetOfTheDeveloperWorksOut() {
        // 1 Vexor and 2 Legions for 8 ISK, with prices in the ratio 2 : 3, gives 2 and 3, extensions 2 and 6
        var result = LinePrices.of(new BigDecimal("8"), List.of(product("Vexor", 1, "2000000"), product("Legion", 2, "3000000")));

        assertThat(units(result)).containsExactly(2L, 3L);
        assertThat(extensions(result)).containsExactly(2L, 6L);
    }

    @Test
    void theLeftoverGoesIntoTheExtensionOfTheBiggestLineAndTheLinesAddUpToTheTotal() {
        // 16,000,001 shares as 4,000,000.25 and 6,000,000.375 a unit, which round down, so the 1 ISK that is left goes to the Legions
        var result = LinePrices.of(new BigDecimal("16000001"), List.of(product("Vexor", 1, "4000000"), product("Legion", 2, "6000000")));

        assertThat(units(result)).containsExactly(4_000_000L, 6_000_000L);
        assertThat(extensions(result)).containsExactly(4_000_000L, 12_000_001L);
        assertThat(extensions(result).stream().mapToLong(Long::longValue).sum()).isEqualTo(16_000_001L);
    }

    @Test
    void aTieGivesTheLeftoverToTheFirstLine() {
        var result = LinePrices.of(new BigDecimal("1001"), List.of(product("A", 1, "10"), product("B", 1, "10")));

        // 500.5 each, rounded half up to 501, so 1,002 is 1 too many, and the first of two equal lines takes that off
        assertThat(units(result)).containsExactly(501L, 501L);
        assertThat(extensions(result)).containsExactly(500L, 501L);
    }

    @Test
    void aSingleProductGetsTheWholeTotalWhateverItsUnitPriceRoundsTo() {
        var result = LinePrices.of(new BigDecimal("100001"), List.of(product("Ishtar", 2, "52000000")));

        // 50,000.5 a unit rounds half up to 50,001, so the extension is the total and not 2 times that
        assertThat(units(result)).containsExactly(50_001L);
        assertThat(extensions(result)).containsExactly(100_001L);
    }

    @Test
    void aProductWithNoPriceMakesEveryUnitPricedAlikeAndSaysSo() {
        var result = LinePrices.of(new BigDecimal("15000000"), List.of(product("Vexor", 1, null), product("Legion", 2, "6000000")));

        assertThat(units(result)).containsExactly(5_000_000L, 5_000_000L);
        assertThat(extensions(result)).containsExactly(5_000_000L, 10_000_000L);
        assertThat(result.evenly()).isTrue();
        // a price of 0 is no price
        assertThat(LinePrices.of(new BigDecimal("1000"), List.of(product("A", 1, "0"), product("B", 1, "5"))).evenly()).isTrue();
    }

    @Test
    void aTotalWithCentsIsRoundedHalfUpToTheWholeIsk() {
        var result = LinePrices.of(new BigDecimal("1000.5"), List.of(product("A", 1, "5")));

        assertThat(extensions(result)).containsExactly(1_001L);
    }

    @Test
    void noProductsGiveNoLines() {
        assertThat(LinePrices.of(new BigDecimal("1000"), List.of()).lines()).isEmpty();
    }

    @Test
    void whateverTheNumbersTheLinesAddUpToTheTotalAndNoPriceIsNegative() {
        var random = new Random(26_11_2026);
        for (int round = 0; round < 500; round++) {
            var products = new ArrayList<LinePrices.Product>();
            for (int i = 0, count = 1 + random.nextInt(5); i < count; i++) {
                products.add(product("P" + i, 1 + random.nextInt(50), random.nextInt(10) == 0 ? null : String.valueOf(1 + random.nextInt(2_000_000_000))));
            }
            var total = new BigDecimal(random.nextInt(2_000_000_000)).add(BigDecimal.valueOf(random.nextInt(100), 2));

            var result = LinePrices.of(total, products);

            assertThat(extensions(result).stream().mapToLong(Long::longValue).sum()).as("round %s", round)
                    .isEqualTo(total.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact());
            assertThat(units(result)).allMatch(unit -> unit >= 0);
        }
    }
}
