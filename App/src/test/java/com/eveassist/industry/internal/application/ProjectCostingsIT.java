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
                BigDecimal.ZERO, null, null), version)).version();
        editing.setConfig(owner, new SetProjectConfigCommand(id, ProjectConfig.defaults().withBuilder(new ProjectBuilder(5, 5,
                BigDecimal.ZERO, Map.of(NANITE, 5, MECHANICAL, 3))).withDefaultFacility(facility), version));
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
        // both ages are there: when the app last confirmed the prices, and when ESI last changed them
        assertThat(result.adjustedPricesAge().confirmedAt()).isNotNull();
        assertThat(result.adjustedPricesAge().esiModifiedAt()).isEqualTo(com.eveassist.TestEsi.PRICES_MODIFIED);
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
                hauler.blueprintCostPerRun(), free, null), project.version()));

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
    void theJitaSplitCostsTheBoughtMaterialsAtTheMidpointOfBuyAndSell() {
        var owner = viewerFor(newSub());
        var id = twoHaulers(owner, azbel(owner, "2"));
        var project = projects.find(owner, id).orElseThrow();
        editing.setConfig(owner, new SetProjectConfigCommand(id, project.config().withPriceSide(PriceSide.SPLIT), project.version()));

        var cost = costings.cost(owner, id).cost();

        // the scenario's prices are 5 sell and 4 buy for Tritanium and 10 and 8 for the Pilot's Mineral, so the same demand costs 1,255 at sell and
        // 1,004 at buy, and at the split, which is 4.5 and 9, half way between them
        assertThat(cost.amount(Component.MATERIALS)).isEqualByComparingTo("1129.5");
    }

    @Test
    void anItemWithOnlyOneSideOfItsJitaPriceIsUnpricedUnderTheSplitAndPricedUnderTheOtherBases() {
        var owner = viewerFor(newSub());
        var id = twoHaulers(owner, azbel(owner, "2"));
        var project = projects.find(owner, id).orElseThrow();
        // a Pilot's Mineral nobody buys: it has a sell price and no buy price
        editing.replacePrices(owner, id, project.version(), java.util.Map.of(PILOTS_MINERAL,
                new com.eveassist.market.JitaQuote(PILOTS_MINERAL, new BigDecimal("10"), null, java.time.Instant.now())));
        var version = projects.find(owner, id).orElseThrow().version();

        editing.setConfig(owner, new SetProjectConfigCommand(id, projects.find(owner, id).orElseThrow().config().withPriceSide(PriceSide.SELL), version));
        assertThat(costings.cost(owner, id).warnings()).noneMatch(warning -> warning.kind() == Kind.UNPRICED_MATERIAL);

        version = projects.find(owner, id).orElseThrow().version();
        editing.setConfig(owner, new SetProjectConfigCommand(id, projects.find(owner, id).orElseThrow().config().withPriceSide(PriceSide.SPLIT), version));
        var result = costings.cost(owner, id);

        assertThat(result.warnings()).contains(new ProjectWarning(Kind.UNPRICED_MATERIAL, PILOTS_MINERAL));
        assertThat(result.quotable()).isFalse();
    }

    @Test
    void aMaterialWithNoAdjustedPriceIsNamedAndDoesNotBlockTheQuote() {
        var owner = viewerFor(newSub());
        var id = twoHaulers(owner, azbel(owner, "2"));
        esiHasAdjustedPrices(false);

        var result = costings.cost(owner, id);

        // read in the game on 2026-11-25 (G16): a material ESI gives no adjusted price adds nothing to the estimated item value, so
        // the install cost is right and the warning is a notice. It names the material, not the item it is a material of.
        assertThat(result.warnings()).contains(new ProjectWarning(Kind.MISSING_ADJUSTED_PRICE, PILOTS_MINERAL));
        assertThat(result.warnings()).noneMatch(warning -> warning.kind() == Kind.MISSING_ADJUSTED_PRICE
                && warning.type().equals(COMPONENT));
        assertThat(result.quotable()).isTrue();
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
    void askingToCostStoresTheCostingAndItsTraceAsTheDraftsLatest() {
        var owner = viewerFor(newSub());
        var id = twoHaulers(owner, azbel(owner, "2"));

        var run = entry.cost(owner, id);

        var stored = costings.latest(owner, id).orElseThrow();
        assertThat(stored.costedProjectVersion()).isEqualTo(run.result().costedProjectVersion());
        assertThat(stored.deliveredCost()).isEqualByComparingTo("11344.98");
        assertThat(stored.quotable()).isTrue();
        var document = stored.document();
        assertThat(document.deliveredCost()).isEqualByComparingTo("11344.984");
        assertThat(document.lines()).extracting(CostingDocumentV1.Line::component).containsExactly("materials", "install_cost",
                "blueprint", "time_value", "hauling", "contract_fee", "surplus_credit");
        assertThat(document.lines().getFirst().amount()).isEqualByComparingTo("1255");
        assertThat(document.quote().mode()).isEqualTo("margin");
        assertThat(document.quote().price()).isEqualByComparingTo("15000");
        assertThat(document.jobs()).extracting(CostingDocumentV1.Job::product, CostingDocumentV1.Job::runs)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(1001, 2L), org.assertj.core.groups.Tuple.tuple(1002, 4L));
        assertThat(document.provenance().adjustedPricesEsiModifiedAt()).isEqualTo(com.eveassist.TestEsi.PRICES_MODIFIED);
        assertThat(document.warnings()).isEmpty();
    }

    @Test
    void theTraceShowsEveryNumberOfAJobInTheShapeOfTheGamesWindows() {
        var owner = viewerFor(newSub());
        var id = twoHaulers(owner, azbel(owner, "2"));
        entry.cost(owner, id);

        var trace = costings.latestTrace(owner, id).orElseThrow();

        assertThat(trace.data().appCommit()).isEqualTo("dev");
        assertThat(trace.data().sdeVersion()).isEqualTo("test-sde");
        assertThat(trace.inputs().priceSide()).isEqualTo("sell");
        assertThat(trace.inputs().industryLevel()).isEqualTo(5);
        assertThat(trace.demands()).extracting(TraceDocumentV1.Demand::type, TraceDocumentV1.Demand::unitsNeeded,
                TraceDocumentV1.Demand::runsNeeded, TraceDocumentV1.Demand::jobs, TraceDocumentV1.Demand::unitsMade)
                .contains(org.assertj.core.groups.Tuple.tuple(1001, 2L, 2L, 1, 2L), org.assertj.core.groups.Tuple.tuple(1002, 4L, 4L, 1, 4L));
        var hauler = trace.jobs().stream().filter(job -> job.product() == 1001).findFirst().orElseThrow();
        assertThat(hauler.runs()).isEqualTo(2);
        assertThat(hauler.me()).isEqualTo(10);
        assertThat(hauler.te()).isEqualTo(20);
        assertThat(hauler.facility().systemCostIndexPercent()).isEqualByComparingTo("2");
        assertThat(hauler.facility().structureTimePercent()).isEqualByComparingTo("20");
        assertThat(hauler.builder().productSkills()).extracting(TraceDocumentV1.Skill::level, skill -> skill.percentPerLevel().intValue())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(5, 1), org.assertj.core.groups.Tuple.tuple(3, 2));
        // the exact product, the figure to two decimals, and what the job uses: 2 x 2 x 0.891 = 3.564, and 100 x 2 x 0.891 = 178.2
        var component = hauler.materials().stream().filter(material -> material.type() == 1002).findFirst().orElseThrow();
        assertThat(component.exactProduct()).isEqualByComparingTo("3.564");
        assertThat(component.roundedToTwoDecimals()).isEqualByComparingTo("3.56");
        assertThat(component.quantity()).isEqualTo(4);
        var tritanium = hauler.materials().stream().filter(material -> material.type() == 34).findFirst().orElseThrow();
        assertThat(tritanium.exactProduct()).isEqualByComparingTo("178.2");
        assertThat(tritanium.quantity()).isEqualTo(179);
        // the duration hover: TE, skills (0.80 x 0.85 x 0.95 x 0.94), structure
        assertThat(hauler.time().baseSecondsPerRun()).isEqualTo(6000);
        assertThat(hauler.time().blueprintFactor()).isEqualByComparingTo("0.8");
        assertThat(hauler.time().skillsAndImplantsFactor()).isEqualByComparingTo("0.60724");
        assertThat(hauler.time().structureFactor()).isEqualByComparingTo("0.8");
        assertThat(hauler.time().exactSeconds()).isEqualByComparingTo("4663.6032");
        assertThat(hauler.time().seconds()).isEqualTo(4664);
        // the cost hover: EIV 1,200, a job fee of 2%, the structure's 4% off it, and the SCC surcharge of 4% of the EIV
        assertThat(hauler.install().estimatedItemValue()).isEqualByComparingTo("1200");
        assertThat(hauler.install().jobFee()).isEqualByComparingTo("24");
        assertThat(hauler.install().structureBonus()).isEqualByComparingTo("-0.96");
        assertThat(hauler.install().grossCost()).isEqualByComparingTo("23.04");
        assertThat(hauler.install().sccSurcharge()).isEqualByComparingTo("48");
        assertThat(hauler.install().total()).isEqualByComparingTo("71.04");
        // the adjusted price each base material was valued at
        assertThat(hauler.eivLines()).extracting(TraceDocumentV1.EivLine::type, TraceDocumentV1.EivLine::baseQuantity)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(1002, 2L), org.assertj.core.groups.Tuple.tuple(34, 100L));
        assertThat(hauler.eivLines()).filteredOn(line -> line.type() == 1002).singleElement()
                .satisfies(line -> assertThat(line.adjustedPrice()).isEqualByComparingTo("100"));
        assertThat(trace.unitPrices()).extracting(TraceDocumentV1.UnitPrice::type).containsExactly(34, 35);
    }

    @Test
    void costingAgainReplacesWhatWasStoredAndPlainCostingStoresNothing() {
        var owner = viewerFor(newSub());
        var id = twoHaulers(owner, azbel(owner, "2"));

        costings.cost(owner, id);
        assertThat(costings.latest(owner, id)).as("a plain cost writes nothing").isEmpty();

        entry.cost(owner, id);
        var first = costings.latest(owner, id).orElseThrow();
        var project = projects.find(owner, id).orElseThrow();
        editing.changeQuantity(owner, new ChangeProjectQuantityCommand(id, HAULER, 3, project.version()));
        entry.cost(owner, id);

        var second = costings.latest(owner, id).orElseThrow();
        assertThat(second.costedProjectVersion()).isEqualTo(project.version() + 1);
        assertThat(second.costedProjectVersion()).isGreaterThan(first.costedProjectVersion());
        assertThat(second.deliveredCost()).isGreaterThan(first.deliveredCost());
        assertThat(jdbc.sql("""
                SELECT count(*) FROM project_costing c JOIN project p ON p.id = c.project_id WHERE p.public_id = :id
                """).param("id", id.value()).query(Integer.class).single()).isOne();
    }

    @Test
    void ifTheRefreshFailsTheStoredAdjustedPricesAreUsedAndTheRunSaysSo() {
        var owner = viewerFor(newSub());
        var id = twoHaulers(owner, azbel(owner, "2"));
        esi.failPrices();

        var run = entry.cost(owner, id);

        assertThat(run.adjustedPricesProblem()).contains("down");
        assertThat(run.result().cost().amount(Component.INSTALL_COST)).isEqualByComparingTo("89.984");
        assertThat(run.result().adjustedPricesAge()).isNotNull();
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
                BigDecimal.ZERO, null, null), project.version()));

        assertThatThrownBy(() -> costings.cost(owner, id)).isInstanceOf(OwnedBlueprintsNotAvailableException.class);
    }
}
