package com.eveassist.market.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.eveassist.esi.PublicContractItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContractFlagsTest {

    private static final int ABYSSAL = 3001;

    private static PublicContractItem copy() {
        return new PublicContractItem(1, 1000, 1, true, true, 10, 20, 5);
    }

    /** As real ESI sends it: not a copy, ME and TE present, and no runs at all (the spec says -1). */
    private static PublicContractItem original() {
        return new PublicContractItem(2, 1001, 1, true, false, 10, 20, null);
    }

    private static PublicContractItem mineral() {
        return new PublicContractItem(4, 34, 100, true, false, null, null, null);
    }

    private static PublicContractItem abyssal() {
        return new PublicContractItem(3, ABYSSAL, 1, true, false, null, null, null);
    }

    private static ContractFlags flags(PublicContractItem... items) {
        return ContractFlags.of(List.of(items), type -> type.value() == ABYSSAL);
    }

    @Test
    void aPackOfCopiesIsNotFlagged() {
        assertThat(flags(copy(), copy()).any()).isFalse();
    }

    @Test
    void aBlueprintOriginalIsFlagged() {
        assertThat(flags(copy(), original())).isEqualTo(new ContractFlags(true, false));
    }

    @Test
    void anOriginalWithZeroMeAndTeIsStillFlagged() {
        var zero = new PublicContractItem(5, 1002, 1, true, false, 0, 0, null);

        assertThat(flags(copy(), zero)).isEqualTo(new ContractFlags(true, false));
    }

    @Test
    void anItemThatIsNotABlueprintIsNotAnOriginalWhateverItsRuns() {
        assertThat(flags(copy(), mineral()).any()).isFalse();
    }

    @Test
    void anAbyssalItemIsFlaggedByItsTypeNotItsName() {
        assertThat(flags(copy(), abyssal())).isEqualTo(new ContractFlags(false, true));
    }

    @Test
    void aContractCanCarryBothFlags() {
        assertThat(flags(original(), abyssal())).isEqualTo(new ContractFlags(true, true));
    }
}
