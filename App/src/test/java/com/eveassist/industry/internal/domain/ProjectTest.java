package com.eveassist.industry.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.industry.FacilityId;
import com.eveassist.industry.ProjectId;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The rules the aggregate keeps on its own: what a quoted project refuses, quantities, and how prices and items are kept. */
class ProjectTest {

    private static final TypeId SHIP = new TypeId(1001);
    private static final TypeId PART = new TypeId(1002);
    private static final TypeId ORE = new TypeId(34);
    private static final PlanPrice PRICE = new PlanPrice(new BigDecimal("5"), new BigDecimal("4"), Instant.parse("2026-11-18T12:00:00Z"));

    private static Project draft() {
        return new Project(new ProjectId(UUID.randomUUID()), "P", 0, ProjectStatus.DRAFT, 0, false, ProjectConfig.defaults(),
                List.of(new PlanProduct(SHIP, 2)), Set.of(), Map.of(), Map.of());
    }

    private static Project quoted() {
        var project = draft();
        project.lock();
        return project;
    }

    @Test
    void aNewProjectStartsAsADraftWithTheUsualDefaults() {
        var project = draft();

        assertThat(project.draft()).isTrue();
        assertThat(project.config().settings()).isEqualTo(ProjectSettings.defaults());
        assertThat(project.config().priceSide()).isEqualTo(PriceSide.SELL);
        assertThat(project.config().defaultPolicy()).isEqualTo(SplitPolicy.LEAST_COST);
        assertThat(project.config().builder()).isEqualTo(ProjectBuilder.NONE);
        assertThat(project.lastQuoteNumber()).isZero();
    }

    @Test
    void aDraftTakesProductsIncludingOnesThePlanDidNotHave() {
        var project = draft();

        project.addProduct(PART, 5);
        project.addProduct(SHIP, 3);
        project.changeQuantity(PART, 7);

        assertThat(project.products()).containsExactlyInAnyOrder(new PlanProduct(SHIP, 5), new PlanProduct(PART, 7));
        project.removeProduct(SHIP);
        assertThat(project.hasProduct(SHIP)).isFalse();
    }

    @Test
    void aQuantityOutsideOneToAMillionIsRefused() {
        var project = draft();

        assertThatThrownBy(() -> project.addProduct(PART, 0)).isInstanceOf(InvalidQuantityException.class);
        assertThatThrownBy(() -> project.changeQuantity(SHIP, 1_000_001)).isInstanceOf(InvalidQuantityException.class);
        assertThat(project.products()).containsExactly(new PlanProduct(SHIP, 2));
    }

    @Test
    void aQuotedProjectRefusesEveryEditOfItsInputs() {
        var project = quoted();
        var edits = List.<Runnable>of(
                () -> project.addProduct(PART, 1), () -> project.changeQuantity(SHIP, 3), () -> project.removeProduct(SHIP),
                () -> project.buyPart(PART), () -> project.buildPart(PART), () -> project.setItem(ProjectItem.typed(SHIP)),
                () -> project.keepItemsFor(Set.of()), () -> project.setPrice(ORE, PRICE), () -> project.addPriceIfMissing(ORE, PRICE),
                () -> project.keepPricesFor(Set.of()), () -> project.settings(ProjectSettings.defaults()),
                () -> project.priceSide(PriceSide.BUY), () -> project.defaultPolicy(SplitPolicy.LEAST_COST),
                () -> project.defaultFacility(null), () -> project.builder(ProjectBuilder.NONE), () -> project.buyer("B", "n"),
                project::lock);

        for (var edit : edits) {
            assertThatThrownBy(edit::run).isInstanceOf(ProjectLockedException.class);
        }
    }

    @Test
    void reopeningTurnsAQuotedProjectBackIntoAnEditableDraft() {
        var project = quoted();

        project.reopen();

        assertThat(project.draft()).isTrue();
        project.addProduct(PART, 1);
        assertThat(project.hasProduct(PART)).isTrue();
    }

    @Test
    void onlyAQuotedProjectCanBeReopened() {
        assertThatThrownBy(() -> draft().reopen()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anAddedPriceDoesNotReplaceOneTheProjectAlreadyHas() {
        var project = draft();
        var older = new PlanPrice(new BigDecimal("1"), null, Instant.parse("2026-11-01T00:00:00Z"));
        project.setPrice(ORE, older);

        project.addPriceIfMissing(ORE, PRICE);
        project.addPriceIfMissing(PART, PRICE);

        assertThat(project.prices()).containsEntry(ORE, older).containsEntry(PART, PRICE);
        project.setPrice(ORE, PRICE);
        assertThat(project.prices()).containsEntry(ORE, PRICE);
    }

    @Test
    void pricesAndItemsNothingNeedsAnyMoreAreDropped() {
        var project = draft();
        project.setPrice(ORE, PRICE);
        project.setPrice(PART, PRICE);
        project.setItem(ProjectItem.typed(SHIP));
        project.setItem(ProjectItem.typed(PART));

        project.keepPricesFor(Set.of(ORE));
        project.keepItemsFor(Set.of(SHIP));

        assertThat(project.prices()).containsOnlyKeys(ORE);
        assertThat(project.items()).containsOnlyKeys(SHIP);
    }

    @Test
    void anItemStartsAtThePlansMeAndTeInTheDefaultFacility() {
        var item = ProjectItem.typed(SHIP);

        assertThat(item.me()).isEqualTo(10);
        assertThat(item.te()).isEqualTo(20);
        assertThat(item.facility()).isNull();
        assertThat(item.policy()).isNull();
        assertThat(item.skillLevels()).isEmpty();
    }

    @Test
    void settingsBuilderFacilityAndBuyerAreKept() {
        var project = draft();
        var facility = new FacilityId(UUID.randomUUID());

        project.priceSide(PriceSide.BUY);
        project.builder(new ProjectBuilder(5, 4, new BigDecimal("4")));
        project.defaultFacility(facility);
        project.defaultPolicy(new SplitPolicy(SplitStrategy.COPIES_FIRST, 86_400L));
        project.buyer("Buyer", "by Friday");

        var config = project.config();
        assertThat(config.priceSide()).isEqualTo(PriceSide.BUY);
        assertThat(config.builder().industry()).isEqualTo(5);
        assertThat(config.defaultFacility()).isEqualTo(facility);
        assertThat(config.defaultPolicy().maxJobSeconds()).isEqualTo(86_400L);
        assertThat(config.buyerName()).isEqualTo("Buyer");
        assertThat(config.note()).isEqualTo("by Friday");
    }

    @Test
    void impossibleLevelsAreRefused() {
        assertThatThrownBy(() -> new ProjectBuilder(6, 0, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectBuilder(0, 0, new BigDecimal("101"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectItem(SHIP, BlueprintSource.TYPED, 11, 20, BigDecimal.ZERO, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectItem(SHIP, BlueprintSource.TYPED, 10, 20, BigDecimal.ZERO, null, null,
                Map.of(ORE, 6))).isInstanceOf(IllegalArgumentException.class);
    }
}
