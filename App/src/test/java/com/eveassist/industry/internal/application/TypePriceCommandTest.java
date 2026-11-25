package com.eveassist.industry.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.industry.ProjectId;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The limits of a typed price, whatever the page allowed: to the cent, from one cent to a thousand trillion ISK. */
class TypePriceCommandTest {

    private static TypePriceCommand price(String value) {
        return new TypePriceCommand(new ProjectId(UUID.randomUUID()), new TypeId(34), new BigDecimal(value), 1);
    }

    @Test
    void aPriceIsKeptToTheCentAndRoundedHalfUp() {
        assertThat(price("7.25").price()).isEqualByComparingTo("7.25");
        assertThat(price("7.254").price()).isEqualByComparingTo("7.25");
        assertThat(price("7.255").price()).isEqualByComparingTo("7.26");
        assertThat(price("10").price().scale()).isEqualTo(2);
    }

    @Test
    void thePriceMustBeAtLeastACentAfterRounding() {
        assertThat(price("0.01").price()).isEqualByComparingTo("0.01");
        assertThat(price("0.005").price()).isEqualByComparingTo("0.01");
        assertThatThrownBy(() -> price("0.004")).isInstanceOf(InvalidPriceException.class);
        assertThatThrownBy(() -> price("0")).isInstanceOf(InvalidPriceException.class);
        assertThatThrownBy(() -> price("-5")).isInstanceOf(InvalidPriceException.class);
    }

    @Test
    void thePriceMustFitTheTableAndNothingIsGuessedForAMissingOne() {
        assertThat(price("1000000000000000").price()).isEqualByComparingTo("1000000000000000");
        assertThatThrownBy(() -> price("1000000000000000.01")).isInstanceOf(InvalidPriceException.class);
        assertThatThrownBy(() -> new TypePriceCommand(new ProjectId(UUID.randomUUID()), new TypeId(34), null, 1))
                .isInstanceOf(InvalidPriceException.class);
    }
}
