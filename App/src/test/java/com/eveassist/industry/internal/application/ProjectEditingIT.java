package com.eveassist.industry.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.IntegrationTest;
import com.eveassist.identity.Viewer;
import com.eveassist.industry.FacilityId;
import com.eveassist.industry.PlanId;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.costing.domain.PlanPrice;
import com.eveassist.industry.internal.costing.domain.PlanProduct;
import com.eveassist.industry.internal.costing.domain.SplitPolicy;
import com.eveassist.industry.internal.costing.domain.SplitStrategy;
import com.eveassist.industry.internal.domain.BlueprintSource;
import com.eveassist.industry.internal.domain.PriceSide;
import com.eveassist.industry.internal.domain.ProjectBuilder;
import com.eveassist.industry.internal.domain.ProjectConfig;
import com.eveassist.industry.internal.domain.ProjectItem;
import com.eveassist.industry.internal.domain.ProjectLockedException;
import com.eveassist.industry.internal.domain.ProjectSettings;
import com.eveassist.industry.internal.facility.application.FacilityNotFoundException;
import com.eveassist.industry.internal.shared.application.UnknownProductException;
import com.eveassist.market.JitaQuote;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The project's use cases against the real tables and the small test SDE: the Test Hauler needs two Test Components and Tritanium,
 * and a Test Component needs Tritanium and Pilot's Mineral. Each test makes its own accounts.
 */
class ProjectEditingIT extends IntegrationTest {

    private static final Instant AS_OF = Instant.parse("2026-11-18T12:00:00Z");
    private static final TypeId NESTED = new TypeId(1005);
    private static final TypeId MID = new TypeId(1006);
    private static final TypeId DEEP = new TypeId(1007);
    private static final TypeId UNPRICED_ORE = new TypeId(36);

    @Autowired
    ProjectEditing editing;

    @Autowired
    ProjectEntry entry;

    @Autowired
    ProjectRepository projects;

    @Autowired
    PlanRepository plans;

    @Autowired
    JdbcClient jdbc;

    private static JitaQuote quote(TypeId type, String sell) {
        return new JitaQuote(type, new BigDecimal(sell), null, AS_OF);
    }

    private static PlanPrice price(String sell) {
        return new PlanPrice(new BigDecimal(sell), null, AS_OF);
    }

    private PlanId planWith(Viewer owner, String name) {
        var id = plans.create(owner, name);
        var plan = plans.find(owner, id).orElseThrow();
        plan.addProduct(HAULER, 3);
        plan.buyPart(COMPONENT);
        plan.setPrice(TRITANIUM, price("5.00"));
        plan.setPrice(COMPONENT, price("40.00"));
        plans.save(owner, plan, new PlanTotal(new BigDecimal("1.00"), "test-sde", Instant.now()));
        return id;
    }

    /** A project of its own, with nothing from a plan. */
    private ProjectId emptyProject(Viewer owner) {
        return projects.create(owner, "Mine", null);
    }

    private long versionOf(Viewer owner, ProjectId id) {
        return projects.find(owner, id).orElseThrow().version();
    }

    @Test
    void startCopiesThePlansProductsBoughtPartsAndPricesAndThenTheProjectIsItsOwn() {
        var owner = viewerFor(newSub());
        var planId = planWith(owner, "Source plan");

        var started = editing.start(owner, new StartProjectCommand(planId, null));

        var project = projects.find(owner, started.project()).orElseThrow();
        assertThat(project.name()).isEqualTo("Source plan");
        assertThat(project.draft()).isTrue();
        assertThat(project.version()).isEqualTo(started.version());
        assertThat(project.products()).containsExactly(new PlanProduct(HAULER, 3));
        assertThat(project.boughtParts()).containsExactly(COMPONENT);
        assertThat(project.prices()).containsOnlyKeys(TRITANIUM, COMPONENT);

        // the plan changes, and then goes: the project does not follow it
        var plan = plans.find(owner, planId).orElseThrow();
        plan.addProduct(SECOND_SHIP, 1);
        plans.save(owner, plan, new PlanTotal(new BigDecimal("2.00"), "test-sde", Instant.now()));
        plans.delete(owner, planId);
        var later = projects.find(owner, started.project()).orElseThrow();
        assertThat(later.products()).containsExactly(new PlanProduct(HAULER, 3));
    }

    @Test
    void aProjectCanStartWithItsOwnNameAndNeverFromAnotherAccountsPlan() {
        var owner = viewerFor(newSub());
        var other = viewerFor(newSub());
        var foreign = planWith(other, "Not yours");

        var named = editing.start(owner, new StartProjectCommand(planWith(owner, "Plan"), "  Golem for a buyer "));

        assertThat(projects.find(owner, named.project()).orElseThrow().name()).isEqualTo("Golem for a buyer");
        assertThatThrownBy(() -> editing.start(owner, new StartProjectCommand(foreign, null)))
                .isInstanceOf(PlanNotFoundException.class);
        assertThatThrownBy(() -> new StartProjectCommand(foreign, "   ")).isInstanceOf(InvalidProjectNameException.class);
        assertThatThrownBy(() -> new StartProjectCommand(foreign, "x".repeat(101))).isInstanceOf(InvalidProjectNameException.class);
    }

    @Test
    void aProductThePlanDidNotHaveIsAddedAndPricesAreTakenOnlyForItemsWithNone() {
        var owner = viewerFor(newSub());
        var id = emptyProject(owner);
        editing.addProduct(owner, new AddProjectProductCommand(id, HAULER, 2, 0), Map.of(
                TRITANIUM, quote(TRITANIUM, "5.00"), COMPONENT, quote(COMPONENT, "40.00")));

        var changed = editing.addProduct(owner, new AddProjectProductCommand(id, SECOND_SHIP, 1, versionOf(owner, id)), Map.of(
                TRITANIUM, quote(TRITANIUM, "99.00"), UNPRICED_ORE, quote(UNPRICED_ORE, "7.00")));

        var project = projects.find(owner, id).orElseThrow();
        assertThat(changed.version()).isEqualTo(project.version());
        assertThat(project.products()).extracting(PlanProduct::type).containsExactlyInAnyOrder(HAULER, SECOND_SHIP);
        // Tritanium was already priced and stays at 5.00, the ore had none and takes 7.00
        assertThat(project.prices().get(TRITANIUM).sell()).isEqualByComparingTo("5.00");
        assertThat(project.prices().get(UNPRICED_ORE).sell()).isEqualByComparingTo("7.00");
    }

    @Test
    void anItemThatCannotBeBuiltIsRefusedAsAProduct() {
        var owner = viewerFor(newSub());
        var id = emptyProject(owner);

        assertThatThrownBy(() -> editing.addProduct(owner, new AddProjectProductCommand(id, TRITANIUM, 1, 0)))
                .isInstanceOf(UnknownProductException.class);
    }

    @Test
    void removingAProductDropsWhatNothingNeedsAndKeepsWhatOthersDo() {
        var owner = viewerFor(newSub());
        var id = emptyProject(owner);
        var version = editing.addProduct(owner, new AddProjectProductCommand(id, HAULER, 1, 0), Map.of()).version();
        version = editing.addProduct(owner, new AddProjectProductCommand(id, SECOND_SHIP, 1, version), Map.of(
                TRITANIUM, quote(TRITANIUM, "5"), new TypeId(35), quote(new TypeId(35), "10"), COMPONENT, quote(COMPONENT, "40"), HAULER, quote(HAULER, "1000"),
                SECOND_SHIP, quote(SECOND_SHIP, "500"), UNPRICED_ORE, quote(UNPRICED_ORE, "7"))).version();
        version = editing.setItem(owner, new SetProjectItemCommand(id, ProjectItem.typed(HAULER), version)).version();
        version = editing.setItem(owner, new SetProjectItemCommand(id, ProjectItem.typed(SECOND_SHIP), version)).version();

        editing.removeProduct(owner, new RemoveProjectProductCommand(id, SECOND_SHIP, version));

        var project = projects.find(owner, id).orElseThrow();
        // the Hauler still needs the Component, Tritanium and Pilot's Mineral, and the second ship's own price and ore are gone
        assertThat(project.prices()).containsOnlyKeys(HAULER, COMPONENT, TRITANIUM, new TypeId(35));
        assertThat(project.items()).containsOnlyKeys(HAULER);
    }

    @Test
    void aMissingProductCannotBeChangedOrRemoved() {
        var owner = viewerFor(newSub());
        var id = emptyProject(owner);

        assertThatThrownBy(() -> editing.changeQuantity(owner, new ChangeProjectQuantityCommand(id, HAULER, 2, 0)))
                .isInstanceOf(ProjectNotFoundException.class);
        assertThatThrownBy(() -> editing.removeProduct(owner, new RemoveProjectProductCommand(id, HAULER, 0)))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void anAddedProductIsFetchedOnlyForItemsTheProjectHasNoPriceFor() {
        var owner = viewerFor(newSub());
        var id = emptyProject(owner);
        esi.sell(1001, "1000.00");
        esi.sell(1002, "40.00");
        esi.sell(1003, "500.00");
        esi.sell(36, "7.00");
        entry.addProduct(owner, new AddProjectProductCommand(id, HAULER, 1, 0));
        esi.reset();
        esi.sell(1003, "500.00");
        esi.sell(36, "7.00");

        entry.addProduct(owner, new AddProjectProductCommand(id, SECOND_SHIP, 1, versionOf(owner, id)));

        // only the second ship and the ore it adds, not the Hauler's Component, Tritanium or Pilot's Mineral
        assertThat(esi.asked()).containsExactlyInAnyOrder(1003, 36);
        assertThat(projects.find(owner, id).orElseThrow().prices()).containsKeys(HAULER, SECOND_SHIP, UNPRICED_ORE);
    }

    @Test
    void aProductRemovedAndAddedAgainIsPricedAgainAtOnce() {
        var owner = viewerFor(newSub());
        var id = emptyProject(owner);
        esi.sell(1001, "1000.00");
        esi.sell(1002, "40.00");
        entry.addProduct(owner, new AddProjectProductCommand(id, HAULER, 1, 0));
        editing.removeProduct(owner, new RemoveProjectProductCommand(id, HAULER, versionOf(owner, id)));
        assertThat(projects.find(owner, id).orElseThrow().prices()).isEmpty();
        esi.reset();
        esi.sell(1001, "1100.00");

        entry.addProduct(owner, new AddProjectProductCommand(id, HAULER, 1, versionOf(owner, id)));

        assertThat(esi.asked()).contains(1001, 1002, 34, 35);
        assertThat(projects.find(owner, id).orElseThrow().prices().get(HAULER).sell()).isEqualByComparingTo("1100.00");
    }

    @Test
    void refreshingTakesNewPricesAndKeepsTheOldOneForAnItemEsiCouldNotAnswer() {
        var owner = viewerFor(newSub());
        var started = editing.start(owner, new StartProjectCommand(planWith(owner, "Plan"), null));
        esi.sell(34, "6.00");
        esi.fail(35);

        var refreshed = entry.refreshPrices(owner, started.project());

        var project = projects.find(owner, started.project()).orElseThrow();
        assertThat(project.prices().get(TRITANIUM).sell()).isEqualByComparingTo("6.00");
        assertThat(refreshed.failedItems()).containsExactly("Pilot's Mineral");
        assertThat(project.version()).isEqualTo(refreshed.project().version());
    }

    @Test
    void aQuotedProjectRefusesEveryEditButStillRenamesAndArchives() {
        var owner = viewerFor(newSub());
        var started = editing.start(owner, new StartProjectCommand(planWith(owner, "Plan"), null));
        var project = projects.find(owner, started.project()).orElseThrow();
        project.lock();
        var version = projects.save(owner, project).orElseThrow();
        esi.reset();
        var id = started.project();

        assertThatThrownBy(() -> editing.changeQuantity(owner, new ChangeProjectQuantityCommand(id, HAULER, 9, version)))
                .isInstanceOf(ProjectLockedException.class);
        assertThatThrownBy(() -> editing.setConfig(owner, new SetProjectConfigCommand(id, ProjectConfig.defaults(), version)))
                .isInstanceOf(ProjectLockedException.class);
        assertThatThrownBy(() -> entry.addProduct(owner, new AddProjectProductCommand(id, SECOND_SHIP, 1, version)))
                .isInstanceOf(ProjectLockedException.class);
        assertThatThrownBy(() -> entry.refreshPrices(owner, id)).isInstanceOf(ProjectLockedException.class);
        assertThat(esi.asked()).as("ESI is not asked for a quoted project").isEmpty();

        editing.rename(owner, new RenameProjectCommand(id, "Renamed"));
        editing.archive(owner, id);
        var after = projects.find(owner, id).orElseThrow();
        assertThat(after.name()).isEqualTo("Renamed");
        assertThat(after.archived()).isTrue();
        assertThat(after.version()).isEqualTo(version);
    }

    @Test
    void aStaleVersionIsRefused() {
        var owner = viewerFor(newSub());
        var id = emptyProject(owner);
        editing.addProduct(owner, new AddProjectProductCommand(id, HAULER, 1, 0));

        assertThatThrownBy(() -> editing.addProduct(owner, new AddProjectProductCommand(id, SECOND_SHIP, 1, 0)))
                .isInstanceOf(ProjectChangedException.class);
        assertThat(projects.find(owner, id).orElseThrow().hasProduct(SECOND_SHIP)).isFalse();
    }

    @Test
    void onlyAPartTheProjectShowsCanBeBoughtOrBuilt() {
        var owner = viewerFor(newSub());
        var id = emptyProject(owner);
        var version = editing.addProduct(owner, new AddProjectProductCommand(id, HAULER, 1, 0)).version();
        version = editing.addProduct(owner, new AddProjectProductCommand(id, NESTED, 1, version)).version();

        version = editing.choosePartSource(owner, new ChooseProjectPartSourceCommand(id, COMPONENT, true, version)).version();
        assertThat(projects.find(owner, id).orElseThrow().boughtParts()).containsExactly(COMPONENT);
        version = editing.choosePartSource(owner, new ChooseProjectPartSourceCommand(id, COMPONENT, false, version)).version();
        assertThat(projects.find(owner, id).orElseThrow().boughtParts()).isEmpty();

        // a product is not a part, an ore is not buildable, and Deep Part is hidden once Mid Part is bought
        long current = editing.choosePartSource(owner, new ChooseProjectPartSourceCommand(id, MID, true, version)).version();
        for (var wrong : Set.of(HAULER, TRITANIUM, DEEP)) {
            assertThatThrownBy(() -> editing.choosePartSource(owner, new ChooseProjectPartSourceCommand(id, wrong, true, current)))
                    .as("type %s", wrong.value()).isInstanceOf(ProjectNotFoundException.class);
        }
    }

    @Test
    void anItemCanBeSetOnlyForSomethingTheProductsCouldNeedAndAFacilityMustBeTheViewers() {
        var owner = viewerFor(newSub());
        var other = viewerFor(newSub());
        var id = emptyProject(owner);
        var version = editing.addProduct(owner, new AddProjectProductCommand(id, HAULER, 1, 0)).version();
        var item = new ProjectItem(COMPONENT, BlueprintSource.TYPED, 8, 14, new BigDecimal("12.5"), null, null);

        version = editing.setItem(owner, new SetProjectItemCommand(id, item, version)).version();
        assertThat(projects.find(owner, id).orElseThrow().items()).containsKey(COMPONENT);

        long current = version;
        assertThatThrownBy(() -> editing.setItem(owner, new SetProjectItemCommand(id, ProjectItem.typed(SECOND_SHIP), current)))
                .as("not needed by this project").isInstanceOf(ProjectNotFoundException.class);
        assertThatThrownBy(() -> editing.setItem(owner, new SetProjectItemCommand(id, ProjectItem.typed(TRITANIUM), current)))
                .as("no blueprint").isInstanceOf(ProjectNotFoundException.class);
        var foreignFacility = facilityOf(other);
        var onForeign = new ProjectItem(COMPONENT, BlueprintSource.TYPED, 10, 20, BigDecimal.ZERO, foreignFacility, null);
        assertThatThrownBy(() -> editing.setItem(owner, new SetProjectItemCommand(id, onForeign, current)))
                .isInstanceOf(FacilityNotFoundException.class);
    }

    private FacilityId facilityOf(Viewer viewer) {
        var id = UUID.randomUUID();
        jdbc.sql("INSERT INTO facility (public_id, owner_account_id, name) VALUES (:id, :owner, 'Azbel')")
                .param("id", id).param("owner", viewer.accountId().value()).update();
        return new FacilityId(id);
    }

    @Test
    void theConfigIsSetInOneGo() {
        var owner = viewerFor(newSub());
        var id = emptyProject(owner);
        var facility = facilityOf(owner);
        var config = new ProjectConfig(
                new ProjectSettings(new BigDecimal("500000"), new BigDecimal("300"), new BigDecimal("10000"),
                        ProjectSettings.QuoteMode.MARKET, new BigDecimal("12.5"), new BigDecimal("-5"), new BigDecimal("1000")),
                PriceSide.BUY, new SplitPolicy(SplitStrategy.COPIES_FIRST, 86_400L), facility,
                new ProjectBuilder(5, 4, new BigDecimal("4")), "Buyer", "by Friday");

        editing.setConfig(owner, new SetProjectConfigCommand(id, config, 0));

        var loaded = projects.find(owner, id).orElseThrow().config();
        assertThat(loaded).usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(config);
    }

    @Test
    void anotherAccountsProjectLooksTheSameAsAMissingOne() {
        var owner = viewerFor(newSub());
        var other = viewerFor(newSub());
        var id = emptyProject(owner);

        assertThatThrownBy(() -> editing.rename(other, new RenameProjectCommand(id, "Mine now"))).isInstanceOf(ProjectNotFoundException.class);
        assertThatThrownBy(() -> editing.archive(other, id)).isInstanceOf(ProjectNotFoundException.class);
        assertThatThrownBy(() -> editing.restore(other, id)).isInstanceOf(ProjectNotFoundException.class);
        assertThatThrownBy(() -> editing.delete(other, id)).isInstanceOf(ProjectNotFoundException.class);
        assertThatThrownBy(() -> editing.addProduct(other, new AddProjectProductCommand(id, HAULER, 1, 0)))
                .isInstanceOf(ProjectNotFoundException.class);
        assertThatThrownBy(() -> entry.refreshPrices(other, id)).isInstanceOf(ProjectNotFoundException.class);

        editing.delete(owner, id);
        assertThat(projects.find(owner, id)).isEmpty();
    }
}
