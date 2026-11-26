package com.eveassist.industry.internal.project.quote.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.industry.FacilityId;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.costing.domain.FacilityTerms;
import com.eveassist.industry.internal.costing.domain.PlanPrice;
import com.eveassist.industry.internal.costing.domain.PlanProduct;
import com.eveassist.industry.internal.costing.domain.SplitPolicy;
import com.eveassist.industry.internal.costing.domain.SplitStrategy;
import com.eveassist.industry.internal.project.application.UnreadableDocumentException;
import com.eveassist.industry.internal.project.domain.BlueprintSource;
import com.eveassist.industry.internal.project.domain.PriceSide;
import com.eveassist.industry.internal.project.domain.Project;
import com.eveassist.industry.internal.project.domain.ProjectBuilder;
import com.eveassist.industry.internal.project.domain.ProjectConfig;
import com.eveassist.industry.internal.project.domain.ProjectItem;
import com.eveassist.industry.internal.project.domain.ProjectSettings;
import com.eveassist.industry.internal.project.domain.ProjectSettings.QuoteMode;
import com.eveassist.industry.internal.project.domain.ProjectStatus;
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
                PriceSide.BUY, new SplitPolicy(SplitStrategy.COPIES_FIRST, 86_400L), AZBEL, new ProjectBuilder(5, 4, new BigDecimal("4"), Map.of(new TypeId(11442), 5, new TypeId(11452), 3)),
                "Buyer", "by Friday");
        var items = Map.of(
                SHIP, new ProjectItem(SHIP, BlueprintSource.TYPED, 8, 14, new BigDecimal("20000000.50"), OTHER,
                        new SplitPolicy(SplitStrategy.LEAST_COST, null)),
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
    void aQuoteStoredWithSkillsOnItsItemsGivesTheBuilderTheHigherLevelOfEach() {
        var snapshot = QuoteInputs.snapshot(project(), both());
        var config = snapshot.config();
        // as quotes were stored before 2026-11-25: no skills on the builder, and each item with its own, which could disagree
        var old = new QuoteInputsV1(snapshot.products(), snapshot.boughtParts(), List.of(
                new QuoteInputsV1.Item(1001, "typed", 10, 20, BigDecimal.ZERO, null, null,
                        List.of(new QuoteInputsV1.Skill(11442, 2), new QuoteInputsV1.Skill(11452, 4))),
                new QuoteInputsV1.Item(1002, "typed", 10, 20, BigDecimal.ZERO, null, null, List.of(new QuoteInputsV1.Skill(11442, 5)))),
                new QuoteInputsV1.Config(config.priceSide(), config.iskPerHour(), config.iskPerCubicMeter(), config.contractFee(), config.quoteMode(),
                        config.marginPercent(), config.premiumPercent(), config.quoteStep(), config.defaultPolicy(), config.defaultFacility(),
                        new QuoteInputsV1.Builder(5, 4, new BigDecimal("4"), null), config.buyerName(), config.note()),
                snapshot.prices(), snapshot.facilities());

        var restored = QuoteInputs.restore(old, both());

        assertThat(restored.config().builder().skillLevels()).containsOnly(
                java.util.Map.entry(new TypeId(11442), 5), java.util.Map.entry(new TypeId(11452), 4));
    }

    @Test
    void thePricesTheUserTypedAreSaidSoInTheSnapshotAndComeBackTyped() {
        var project = project();
        project.typePrice(ORE, new BigDecimal("4.25"), AS_OF);

        var snapshot = QuoteInputs.snapshot(project, both());
        var restored = QuoteInputs.restore(snapshot, both());

        assertThat(snapshot.prices()).filteredOn(price -> price.type() == ORE.value()).singleElement().extracting(QuoteInputsV1.Price::typed).isEqualTo(true);
        assertThat(snapshot.prices()).filteredOn(price -> price.type() == PART.value()).singleElement().extracting(QuoteInputsV1.Price::typed).isEqualTo(false);
        assertThat(restored.typedPrices()).containsExactly(ORE);
        // a typed price is one number on both sides
        assertThat(restored.prices().get(ORE).sell()).isEqualByComparingTo("4.25");
        assertThat(restored.prices().get(ORE).buy()).isEqualByComparingTo("4.25");
    }

    @Test
    void aSnapshotMadeBeforeTypedPricesExistedHasNoTypedPrices() {
        var snapshot = QuoteInputs.snapshot(project(), both());
        // a price as an old document has it: without the flag, which reads as false
        var old = new QuoteInputsV1(snapshot.products(), snapshot.boughtParts(), snapshot.items(), snapshot.config(),
                snapshot.prices().stream().map(price -> new QuoteInputsV1.Price(price.type(), price.sell(), price.buy(), price.asOf())).toList(),
                snapshot.facilities());

        assertThat(QuoteInputs.restore(old, both()).typedPrices()).isEmpty();
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
