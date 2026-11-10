package com.eveassist.industry.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.sde.TypeId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TryPlanStateTest {

    @Test
    void roundTripsProductsAndBoughtParts() {
        var state = new TryPlanState(
                List.of(new TryPlanState.ProductEntry(1001, 2), new TryPlanState.ProductEntry(1004, 200)),
                Set.of(new TypeId(1002), new TypeId(34)));

        var text = state.format();

        assertThat(text).isEqualTo("p=1001:2,1004:200;b=34,1002");
        assertThat(TryPlanState.parse(text).products()).containsExactlyElementsOf(state.products());
        assertThat(TryPlanState.parse(text).bought()).containsExactlyInAnyOrderElementsOf(state.bought());
    }

    @Test
    void anEmptyPlanRoundTrips() {
        assertThat(TryPlanState.empty().format()).isEqualTo("p=;b=");
        assertThat(TryPlanState.parse("p=;b=").products()).isEmpty();
    }

    @Test
    void textThatIsNotAPlanIsTakenAsEmpty() {
        for (var text : new String[] {null, "", "hello", "p=1001;b=", "p=1001:x;b=", "p=1001:1", "p=1001:1;b=a",
                "p=1001:1;b=1;drop table", "p=-1:1;b=", "p=1234567890:1;b="}) {
            assertThat(TryPlanState.parse(text).products()).as("%s", text).isEmpty();
            assertThat(TryPlanState.parse(text).bought()).as("%s", text).isEmpty();
        }
    }

    @Test
    void textTooLongToBeAPlanIsTakenAsEmpty() {
        var long1 = "p=" + "1001:1,".repeat(1_000) + "1001:1;b=";

        assertThat(long1.length()).isGreaterThan(TryPlanState.LONGEST);
        assertThat(TryPlanState.parse(long1).products()).isEmpty();
    }
}
