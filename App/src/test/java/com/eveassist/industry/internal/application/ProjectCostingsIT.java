package com.eveassist.industry.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.IntegrationTest;
import com.eveassist.identity.Viewer;
import com.eveassist.industry.FacilityId;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.domain.BlueprintSource;
import com.eveassist.industry.internal.domain.CostLine.Component;
import com.eveassist.industry.internal.domain.PriceSide;
import com.eveassist.industry.internal.domain.ProjectBuilder;
import com.eveassist.industry.internal.domain.ProjectConfig;
import com.eveassist.industry.internal.domain.ProjectItem;
import com.eveassist.industry.internal.domain.ProjectLockedException;
import com.eveassist.industry.internal.domain.ProjectWarning;
import com.eveassist.industry.internal.domain.ProjectWarning.Kind;
import com.eveassist.industry.internal.domain.SplitPolicy;
import com.eveassist.industry.internal.domain.SplitStrategy;
import com.eveassist.market.AdjustedPriceService;
import com.eveassist.market.JitaQuote;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Costing a project from its tables and the test SDE, checked against arithmetic done by hand from the fixture's recipes. The Hauler
 * (6,000 s a run) needs two Test Components and 100 Tritanium; a Component (3,000 s a run) needs 10 Tritanium and 5 Pilot's Mineral.
 * The facility is an Azbel's: materials -1%, time -20%, job fee -4%, an index of 2% and the SCC surcharge of 4%. The builder has
 * Industry V and Advanced Industry V, and the Hauler's blueprint also saves time with Nanite Engineering (1% a level, here V)
 * and Mechanical Engineering (2% a level, here III).
 */
class ProjectCostingsIT extends IntegrationTest {

    private static final Instant AS_OF = Instant.parse("2026-11-18T12:00:00Z");
    private static final TypeId NANITE = new TypeId(11442);
    private static final TypeId MECHANICAL = new TypeId(11452);
    @Autowired
    ProjectCostings costings;

    @Autowired
    ProjectEditing editing;

    @Autowired
    ProjectRepository projects;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AdjustedPriceService adjusted;

    @Autowired
    ProjectEntry entry;

    /** ESI's adjusted prices as the fake ESI gives them, refreshed into the shared table the way the app does it. */
    private void esiHasAdjustedPrices(boolean withPilotsMineral) {
        esi.reset();
        esi.adjustedPrice(TRITANIUM.value(), "4");
        esi.adjustedPrice(COMPONENT.value(), "100");
        if (withPilotsMineral) {
            esi.adjustedPrice(PILOTS_MINERAL.value(), "8");
        }
        adjusted.refresh();
    }

    @BeforeEach
    void giveCostingItsAdjustedPrices() {
        esiHasAdjustedPrices(true);
    }

    private FacilityId azbel(Viewer owner, String index) {
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO facility (public_id, owner_account_id, name, structure_material_percent, structure_time_percent,
                    structure_cost_percent, system_cost_index_percent, scc_surcharge_percent)
                VALUES (:id, :owner, 'Azbel', 1, 20, 4, :index, 4)
                """)
                .param("id", id).param("owner", owner.accountId().value()).param("index", new BigDecimal(index)).update();
        return new FacilityId(id);
    }

    private static JitaQuote quote(TypeId type, String sell, String buy) {
        return new JitaQuote(type, new BigDecimal(sell), new BigDecimal(buy), AS_OF);
    }

    /** Two Haulers, priced, in a Draft with the builder above and the given facility as the default (or none). */
    private ProjectId twoHaulers(Viewer owner, FacilityId facility) {
        var id = projects.create(owner, "Two Haulers", null);
        var version = editing.addProduct(owner, new AddProjectProductCommand(id, HAULER, 2, 0), Map.of(
                TRITANIUM, quote(TRITANIUM, "5", "4"), PILOTS_MINERAL, quote(PILOTS_MINERAL, "10", "8"))).version();
        version = editing.setItem(owner, new SetProjectItemCommand(id, new ProjectItem(HAULER, BlueprintSource.TYPED, 10, 20,
                BigDecimal.ZERO, null, null, Map.of(NANITE, 5, MECHANICAL, 3)), version)).version();
        editing.setConfig(owner, new SetProjectConfigCommand(id, ProjectConfig.defaults().withBuilder(new ProjectBuilder(5, 5,
                BigDecimal.ZERO)).withDefaultFacility(facility), version));
        return id;
    }

    @Test
    void costsAProjectFromItsOwnInputsToTheHandWorkedNumbers() {
        var owner = viewerFor(newSub());
        var id = twoHaulers(owner, azbel(owner, "2"));
        var stored = projects.find(owner, id).orElseThrow();

        var result = costings.cost(owner, id);

        var cost = result.cost();
        // Hauler, 2 runs at ME 10 and the Azbel's 1% (x 0.891): Components ceil(3.56) = 4, Tritanium ceil(178.20) = 179.
        // The 4 Components are 4 runs: Tritanium ceil(35.64) = 36, Pilot's Mineral ceil(17.82) = 18.
        assertThat(cost.jobs()).extracting(job -> job.job().product().value(), job -> job.job().runs())
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(1001, 2L), org.assertj.core.groups.Tuple.tuple(1002, 4L));
        assertThat(cost.bought()).extracting(item -> item.type().value(), item -> item.quantity())
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(34, 215L), org.assertj.core.groups.Tuple.tuple(35, 18L));
        // 215 x 5 + 18 x 10, at the project's own Jita sell prices
        assertThat(cost.amount(Component.MATERIALS)).isEqualByComparingTo("1255");
        // Hauler EIV: 4 x 100 + 200 x 4 = 1,200. Component EIV: 40 x 4 + 20 x 8 = 320. Each x (2% x 0.96 + 4%) = 0.0592
        assertThat(cost.amount(Component.INSTALL_COST)).isEqualByComparingTo("89.984");
        // 6,000 x 2 x 0.8 x 0.68 x 0.95 x 0.94 x 0.8 = 4,663.6 s, and 3,000 x 4 x 0.8 x 0.68 x 0.8 = 5,222.4 s
        assertThat(cost.totalSeconds()).isEqualTo(4664 + 5222);
        assertThat(cost.longestJobSeconds()).isEqualTo(5222);
        assertThat(cost.deliveredCost()).isEqualByComparingTo("11344.984");
        // the margin quote: 11,344.984 / 0.8 = 14,181.23, rounded up to the 1,000 ISK step
        assertThat(cost.quote().price()).isEqualByComparingTo("15000");
        assertThat(result.warnings()).isEmpty();
        assertThat(result.quotable()).isTrue();
        assertThat(result.costedProjectVersion()).isEqualTo(stored.version());
        assertThat(result.sdeVersion()).isEqualTo("test-sde");
        assertThat(result.pricesAsOf()).isEqualTo(AS_OF);
        assertThat(result.adjustedPricesAsOf()).isNotNull();
        // costing writes nothing
        assertThat(projects.find(owner, id).orElseThrow().version()).isEqualTo(stored.version());
    }

    @Test
    void anItemWithNoFacilityIsCostedWithNoneAndTheResultSaysSo() {
        var owner = viewerFor(newSub());
        var id = twoHaulers(owner, null);

        var result = costings.cost(owner, id);

        assertThat(result.warnings()).contains(new ProjectWarning(Kind.NO_FACILITY, HAULER), new ProjectWarning(Kind.NO_FACILITY, COMPONENT));
        assertThat(result.cost().amount(Component.INSTALL_COST)).isEqualByComparingTo("0");
        assertThat(result.quotable()).isFalse();
    }

    @Test
    void anItemsOwnFacilityReplacesTheProjectsDefault() {
        var owner = viewerFor(newSub());
        var id = twoHaulers(owner, azbel(owner, "2"));
        var free = azbel(owner, "0");
        var project = projects.find(owner, id).orElseThrow();
        var hauler = project.items().get(HAULER);
        editing.setItem(owner, new SetProjectItemCommand(id, new ProjectItem(HAULER, hauler.source(), hauler.me(), hauler.te(),
                hauler.blueprintCostPerRun(), free, null, hauler.skillLevels()), project.version()));

        var cost = costings.cost(owner, id).cost();

        // the Hauler's job fee is 0 at an index of 0 but the SCC surcharge of 4% of 1,200 is 48. The Component keeps 18.944
        assertThat(cost.amount(Component.INSTALL_COST)).isEqualByComparingTo("66.944");
    }

    @Test
    void theProjectsPriceSideDecidesWhichJitaPriceIsUsed() {
        var owner = viewerFor(newSub());
        var id = twoHaulers(owner, azbel(owner, "2"));
        var project = projects.find(owner, id).orElseThrow();
        editing.setConfig(owner, new SetProjectConfigCommand(id, project.config().withPriceSide(PriceSide.BUY), project.version()));

        var cost = costings.cost(owner, id).cost();

        assertThat(cost.amount(Component.MATERIALS)).isEqualByComparingTo("1004");
    }

    @Test
    void anItemWithNoAdjustedPriceWarnsAndBlocksTheQuote() {
        var owner = viewerFor(newSub());
        var id = twoHaulers(owner, azbel(owner, "2"));
        esiHasAdjustedPrices(false);

        var result = costings.cost(owner, id);

        assertThat(result.warnings()).contains(new ProjectWarning(Kind.MISSING_ADJUSTED_PRICE, COMPONENT));
        assertThat(result.quotable()).isFalse();
    }

    @Test
    void aTimeBoundShowsWhatSplittingCostsAndWhatItBuys() {
        var owner = viewerFor(newSub());
        var id = twoHaulers(owner, azbel(owner, "2"));
        var project = projects.find(owner, id).orElseThrow();
        editing.setConfig(owner, new SetProjectConfigCommand(id, project.config()
                .withDefaultPolicy(new SplitPolicy(SplitStrategy.LEAST_COST, 3_000L)), project.version()));

        var comparison = costings.cost(owner, id).comparison();

        // A Hauler run is 2,331.8 s, so each of its 2 runs is a job; a Component run is 1,305.6 s, so 2 runs fit a job, and 4 are 2 jobs.
        // Splitting the Hauler needs ceil(89.1) = 90 Tritanium a job, 180 instead of 179: one more at 5 ISK. The longest job falls
        // from 5,222 s to 2,611 s.
        assertThat(comparison.chosen().jobs()).hasSize(4);
        assertThat(comparison.leastCost().jobs()).hasSize(2);
        assertThat(comparison.deliveredCostDelta()).isEqualByComparingTo("5");
        assertThat(comparison.longestJobSecondsSaved()).isEqualTo(2611);
        assertThat(comparison.costPerHourSaved()).isNotNull();
    }

    @Test
    void aQuotedProjectAnEmptyOneAndAnotherAccountsAreRefused() {
        var owner = viewerFor(newSub());
        var other = viewerFor(newSub());
        var id = twoHaulers(owner, azbel(owner, "2"));
        var empty = projects.create(owner, "Empty", null);

        assertThatThrownBy(() -> costings.cost(owner, empty)).isInstanceOf(NothingToCostException.class);
        assertThatThrownBy(() -> costings.cost(other, id)).isInstanceOf(ProjectNotFoundException.class);
        var project = projects.find(owner, id).orElseThrow();
        project.lock();
        projects.save(owner, project);
        assertThatThrownBy(() -> costings.cost(owner, id)).isInstanceOf(ProjectLockedException.class);
    }

    @Test
    void askingToCostRefreshesTheAdjustedPricesFirstAndUsesThem() {
        var owner = viewerFor(newSub());
        var id = twoHaulers(owner, azbel(owner, "2"));
        esi.reset();
        esi.adjustedPrice(TRITANIUM.value(), "40");
        esi.adjustedPrice(PILOTS_MINERAL.value(), "80");
        esi.adjustedPrice(COMPONENT.value(), "1000");

        var run = entry.cost(owner, id);

        // ten times the adjusted prices, so ten times the estimated item values and the install cost: 899.84, not 89.984
        assertThat(esi.pricesAsked()).isEqualTo(1);
        assertThat(run.adjustedPricesProblem()).isNull();
        assertThat(run.result().cost().amount(Component.INSTALL_COST)).isEqualByComparingTo("899.84");
    }

    @Test
    void ifTheRefreshFailsTheStoredAdjustedPricesAreUsedAndTheRunSaysSo() {
        var owner = viewerFor(newSub());
        var id = twoHaulers(owner, azbel(owner, "2"));
        esi.failPrices();

        var run = entry.cost(owner, id);

        assertThat(run.adjustedPricesProblem()).contains("down");
        assertThat(run.result().cost().amount(Component.INSTALL_COST)).isEqualByComparingTo("89.984");
        assertThat(run.result().adjustedPricesAsOf()).isNotNull();
    }

    @Test
    void aQuotedProjectAndAnEmptyOneAreRefusedBeforeEsiIsAsked() {
        var owner = viewerFor(newSub());
        var id = twoHaulers(owner, azbel(owner, "2"));
        var empty = projects.create(owner, "Empty", null);
        var project = projects.find(owner, id).orElseThrow();
        project.lock();
        projects.save(owner, project);
        esi.reset();

        assertThatThrownBy(() -> entry.cost(owner, empty)).isInstanceOf(NothingToCostException.class);
        assertThatThrownBy(() -> entry.cost(owner, id)).isInstanceOf(ProjectLockedException.class);
        assertThatThrownBy(() -> entry.cost(viewerFor(newSub()), id)).isInstanceOf(ProjectNotFoundException.class);
        assertThat(esi.pricesAsked()).as("ESI is not asked for these").isZero();
    }

    @Test
    void anOwnedBlueprintCannotBeCostedUntilTheyAreRead() {
        var owner = viewerFor(newSub());
        var id = twoHaulers(owner, azbel(owner, "2"));
        var project = projects.find(owner, id).orElseThrow();
        editing.setItem(owner, new SetProjectItemCommand(id, new ProjectItem(COMPONENT, BlueprintSource.OWNED, 10, 20,
                BigDecimal.ZERO, null, null, Map.of()), project.version()));

        assertThatThrownBy(() -> costings.cost(owner, id)).isInstanceOf(OwnedBlueprintsNotAvailableException.class);
    }
}
