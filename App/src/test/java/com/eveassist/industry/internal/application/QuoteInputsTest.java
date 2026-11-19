package com.eveassist.industry.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.industry.FacilityId;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.domain.BlueprintSource;
import com.eveassist.industry.internal.domain.FacilityTerms;
import com.eveassist.industry.internal.domain.PlanPrice;
import com.eveassist.industry.internal.domain.PlanProduct;
import com.eveassist.industry.internal.domain.PriceSide;
import com.eveassist.industry.internal.domain.Project;
import com.eveassist.industry.internal.domain.ProjectBuilder;
import com.eveassist.industry.internal.domain.ProjectConfig;
import com.eveassist.industry.internal.domain.ProjectItem;
import com.eveassist.industry.internal.domain.ProjectSettings;
import com.eveassist.industry.internal.domain.ProjectSettings.QuoteMode;
import com.eveassist.industry.internal.domain.ProjectStatus;
import com.eveassist.industry.internal.domain.SplitPolicy;
import com.eveassist.industry.internal.domain.SplitStrategy;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A Draft into a quote's inputs and back: nothing is lost, and what could not be restored as it was is reported. */
class QuoteInputsTest {

    private static final TypeId SHIP = new TypeId(1001);
    private static final TypeId PART = new TypeId(1002);
    private static final TypeId ORE = new TypeId(34);
    private static final FacilityId AZBEL = new FacilityId(UUID.randomUUID());
    private static final FacilityId OTHER = new FacilityId(UUID.randomUUID());
    private static final Instant AS_OF = Instant.parse("2026-11-18T12:00:00Z");

    private static FacilityTerms terms(String index) {
        return new FacilityTerms(new BigDecimal("1"), BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO, new BigDecimal("4"),
                new BigDecimal(index), BigDecimal.ZERO, new BigDecimal("4"));
    }

    private static Project project() {
        var config = new ProjectConfig(
                new ProjectSettings(new BigDecimal("500000"), new BigDecimal("300"), new BigDecimal("10000"), QuoteMode.MARKET,
                        new BigDecimal("12.5"), new BigDecimal("-5"), new BigDecimal("1000")),
                PriceSide.BUY, new SplitPolicy(SplitStrategy.COPIES_FIRST, 86_400L), AZBEL, new ProjectBuilder(5, 4, new BigDecimal("4")),
                "Buyer", "by Friday");
        var items = Map.of(
                SHIP, new ProjectItem(SHIP, BlueprintSource.TYPED, 8, 14, new BigDecimal("20000000.50"), OTHER,
                        new SplitPolicy(SplitStrategy.LEAST_COST, null), Map.of(new TypeId(11442), 5, new TypeId(11452), 3)),
                PART, ProjectItem.typed(PART));
        return new Project(new ProjectId(UUID.randomUUID()), "P", 7, ProjectStatus.DRAFT, 2, false, config,
                List.of(new PlanProduct(SHIP, 3)), Set.of(PART), items,
                Map.of(ORE, new PlanPrice(new BigDecimal("3.89"), new BigDecimal("3.10"), AS_OF), PART, new PlanPrice(null, new BigDecimal("12"), AS_OF)));
    }

    private static Map<FacilityId, FacilityTerms> both() {
        return Map.of(AZBEL, terms("2.002122"), OTHER, terms("0"));
    }

    @Test
    void aDraftIsRestoredFromItsSnapshotWithNothingLost() {
        var project = project();

        var restored = QuoteInputs.restore(QuoteInputs.snapshot(project, both()), both());

        assertThat(restored.products()).isEqualTo(project.products());
        assertThat(restored.boughtParts()).isEqualTo(project.boughtParts());
        assertThat(restored.items()).usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(project.items());
        assertThat(restored.prices()).usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(project.prices());
        assertThat(restored.config()).usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(project.config());
        assertThat(restored.changedFacilities()).isEmpty();
        assertThat(restored.goneFacilities()).isEmpty();
    }

    @Test
    void theSnapshotHoldsTheFacilitiesValuesAsWellAsTheirIds() {
        var snapshot = QuoteInputs.snapshot(project(), both());

        assertThat(snapshot.facilities()).extracting(QuoteInputsV1.FacilityValues::id)
                .containsExactlyInAnyOrder(AZBEL.value().toString(), OTHER.value().toString());
        assertThat(snapshot.facilities()).filteredOn(values -> values.id().equals(AZBEL.value().toString())).singleElement()
                .satisfies(values -> assertThat(values.systemCostIndexPercent()).isEqualByComparingTo("2.002122"));
    }

    @Test
    void aFacilityThatIsGoneIsLeftOutAndReported() {
        var snapshot = QuoteInputs.snapshot(project(), both());

        var restored = QuoteInputs.restore(snapshot, Map.of(OTHER, terms("0")));

        assertThat(restored.goneFacilities()).containsExactly(AZBEL);
        assertThat(restored.config().defaultFacility()).isNull();
        // the item that used the other facility, which still exists, keeps it
        assertThat(restored.items().get(SHIP).facility()).isEqualTo(OTHER);
    }

    @Test
    void aFacilityWhoseValuesChangedIsKeptAndReported() {
        var snapshot = QuoteInputs.snapshot(project(), both());

        var restored = QuoteInputs.restore(snapshot, Map.of(AZBEL, terms("3.5"), OTHER, terms("0.000")));

        // the other one has the same value with another scale, which is not a change
        assertThat(restored.changedFacilities()).containsExactly(AZBEL);
        assertThat(restored.config().defaultFacility()).isEqualTo(AZBEL);
    }

    @Test
    void aSnapshotTheDomainNoLongerAcceptsIsUnreadableAndNotHalfApplied() {
        var good = QuoteInputs.snapshot(project(), both());
        var tampered = new QuoteInputsV1(good.products(), good.boughtParts(),
                List.of(new QuoteInputsV1.Item(1001, "typed", 11, 20, BigDecimal.ZERO, null, null, List.of())), good.config(),
                good.prices(), good.facilities());

        assertThatThrownBy(() -> QuoteInputs.restore(tampered, both())).isInstanceOf(UnreadableDocumentException.class);
    }
}
