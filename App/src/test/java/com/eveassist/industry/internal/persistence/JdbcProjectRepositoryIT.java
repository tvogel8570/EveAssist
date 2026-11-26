package com.eveassist.industry.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.IntegrationTest;
import com.eveassist.identity.Viewer;
import com.eveassist.industry.FacilityId;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.facility.application.FacilityNotFoundException;
import com.eveassist.industry.internal.plan.application.PlanRepository;
import com.eveassist.industry.internal.application.ProjectRepository;
import com.eveassist.industry.internal.domain.BlueprintSource;
import com.eveassist.industry.internal.costing.domain.PlanPrice;
import com.eveassist.industry.internal.costing.domain.PlanProduct;
import com.eveassist.industry.internal.domain.PriceSide;
import com.eveassist.industry.internal.domain.Project;
import com.eveassist.industry.internal.domain.ProjectBuilder;
import com.eveassist.industry.internal.domain.ProjectConfig;
import com.eveassist.industry.internal.domain.ProjectItem;
import com.eveassist.industry.internal.domain.ProjectSettings;
import com.eveassist.industry.internal.domain.ProjectSettings.QuoteMode;
import com.eveassist.industry.internal.domain.ProjectStatus;
import com.eveassist.industry.internal.costing.domain.SplitPolicy;
import com.eveassist.industry.internal.costing.domain.SplitStrategy;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The Draft's tables through the repository: what is kept, what a save replaces, whose it is, and which version it is. */
class JdbcProjectRepositoryIT extends IntegrationTest {

    private static final Instant AS_OF = Instant.parse("2026-11-18T12:00:00Z");
    private static final TypeId SKILL = new TypeId(11442);

    @Autowired
    ProjectRepository projects;

    @Autowired
    PlanRepository plans;

    @Autowired
    JdbcClient jdbc;

    private FacilityId facilityOf(Viewer viewer) {
        var id = UUID.randomUUID();
        jdbc.sql("INSERT INTO facility (public_id, owner_account_id, name) VALUES (:id, :owner, 'Azbel')")
                .param("id", id).param("owner", viewer.accountId().value()).update();
        return new FacilityId(id);
    }

    private ProjectId newProject(Viewer owner) {
        return projects.create(owner, "Mine", null);
    }

    /**
     * Compares by value, with numbers by their value and not their scale: the tables store 0 as 0.00, and BigDecimal.equals tells them
     * apart. A use case that asks whether settings changed must compare the same way.
     */
    private static void assertSameValues(Object actual, Object expected) {
        assertThat(actual).usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(expected);
    }

    private int count(String table, ProjectId id) {
        return jdbc.sql("SELECT count(*) FROM " + table + " t JOIN project p ON p.id = t.project_id WHERE p.public_id = :id")
                .param("id", id.value()).query(Integer.class).single();
    }

    @Test
    void createsAnEmptyDraftWithTheUsualDefaults() {
        var owner = viewerFor(newSub());

        var project = projects.find(owner, newProject(owner)).orElseThrow();

        assertThat(project.name()).isEqualTo("Mine");
        assertThat(project.status()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(project.version()).isZero();
        assertThat(project.lastQuoteNumber()).isZero();
        assertThat(project.archived()).isFalse();
        assertSameValues(project.config(), ProjectConfig.defaults());
        assertThat(project.products()).isEmpty();
        assertThat(project.items()).isEmpty();
        assertThat(project.prices()).isEmpty();
    }

    @Test
    void savesAndLoadsEveryInput() {
        var owner = viewerFor(newSub());
        var facility = facilityOf(owner);
        var id = newProject(owner);
        var project = projects.find(owner, id).orElseThrow();
        project.addProduct(HAULER, 3);
        project.addProduct(SECOND_SHIP, 1);
        project.buyPart(COMPONENT);
        project.setItem(new ProjectItem(HAULER, BlueprintSource.TYPED, 8, 14, new BigDecimal("20000000.50"), facility,
                new SplitPolicy(SplitStrategy.COPIES_FIRST, 172_800L)));
        project.setItem(ProjectItem.typed(SECOND_SHIP));
        project.setPrice(TRITANIUM, new PlanPrice(new BigDecimal("3.89"), new BigDecimal("3.10"), AS_OF));
        project.setPrice(COMPONENT, new PlanPrice(null, new BigDecimal("12.00"), AS_OF));
        project.settings(new ProjectSettings(new BigDecimal("500000"), new BigDecimal("300"), new BigDecimal("10000"),
                QuoteMode.MARKET, new BigDecimal("12.5"), new BigDecimal("-5"), new BigDecimal("1000")));
        project.priceSide(PriceSide.BUY);
        project.defaultPolicy(new SplitPolicy(SplitStrategy.LEAST_COST, null));
        project.defaultFacility(facility);
        project.builder(new ProjectBuilder(5, 4, new BigDecimal("4"), Map.of(SKILL, 5, new TypeId(11452), 3)));
        project.buyer("Buyer", "deliver by contract");

        assertThat(projects.save(owner, project)).contains(1L);

        var loaded = projects.find(owner, id).orElseThrow();
        assertThat(loaded.name()).isEqualTo("Mine");
        assertThat(loaded.version()).isEqualTo(1);
        assertThat(loaded.products()).containsExactlyInAnyOrder(new PlanProduct(HAULER, 3), new PlanProduct(SECOND_SHIP, 1));
        assertThat(loaded.boughtParts()).containsExactly(COMPONENT);
        assertSameValues(loaded.items(), project.items());
        assertSameValues(loaded.prices(), project.prices());
        assertSameValues(loaded.config(), project.config());
    }

    @Test
    void aSaveReplacesWhatWasStoredIncludingTheBuildersSkills() {
        var owner = viewerFor(newSub());
        var id = newProject(owner);
        var project = projects.find(owner, id).orElseThrow();
        project.addProduct(HAULER, 3);
        project.addProduct(SECOND_SHIP, 1);
        project.buyPart(COMPONENT);
        project.builder(new ProjectBuilder(0, 0, BigDecimal.ZERO, Map.of(SKILL, 5)));
        project.setItem(ProjectItem.typed(HAULER));
        project.setItem(ProjectItem.typed(SECOND_SHIP));
        project.setPrice(TRITANIUM, new PlanPrice(new BigDecimal("3"), null, AS_OF));
        project.setPrice(PILOTS_MINERAL, new PlanPrice(new BigDecimal("4"), null, AS_OF));
        projects.save(owner, project);

        var smaller = projects.find(owner, id).orElseThrow();
        smaller.removeProduct(SECOND_SHIP);
        smaller.buildPart(COMPONENT);
        smaller.keepItemsFor(Set.of(HAULER));
        smaller.builder(new ProjectBuilder(0, 0, BigDecimal.ZERO, Map.of(new TypeId(11452), 2)));
        smaller.keepPricesFor(Set.of(TRITANIUM));
        projects.save(owner, smaller);

        var loaded = projects.find(owner, id).orElseThrow();
        assertThat(loaded.products()).containsExactly(new PlanProduct(HAULER, 3));
        assertThat(loaded.boughtParts()).isEmpty();
        assertThat(loaded.items()).containsOnlyKeys(HAULER);
        assertThat(loaded.config().builder().skillLevels()).containsExactly(Map.entry(new TypeId(11452), 2));
        assertThat(loaded.prices()).containsOnlyKeys(TRITANIUM);
        assertThat(count("project_skill", id)).isEqualTo(1);
    }

    @Test
    void aStaleVersionIsRefusedAndChangesNothing() {
        var owner = viewerFor(newSub());
        var id = newProject(owner);
        var first = projects.find(owner, id).orElseThrow();
        var second = projects.find(owner, id).orElseThrow();
        first.addProduct(SECOND_SHIP, 1);
        second.addProduct(HAULER, 1);

        assertThat(projects.save(owner, first)).contains(1L);
        assertThat(projects.save(owner, second)).isEmpty();

        var loaded = projects.find(owner, id).orElseThrow();
        assertThat(loaded.products()).containsExactly(new PlanProduct(SECOND_SHIP, 1));
    }

    @Test
    void anotherAccountsProjectLooksTheSameAsAMissingOne() {
        var owner = viewerFor(newSub());
        var other = viewerFor(newSub());
        var id = newProject(owner);
        var project = projects.find(owner, id).orElseThrow();
        project.addProduct(HAULER, 1);

        assertThat(projects.find(other, id)).isEmpty();
        assertThat(projects.save(other, project)).isEmpty();
        assertThat(projects.rename(other, id, "Stolen")).isFalse();
        assertThat(projects.setArchived(other, id, true)).isFalse();
        assertThat(projects.delete(other, id)).isFalse();

        var kept = projects.find(owner, id).orElseThrow();
        assertThat(kept.name()).isEqualTo("Mine");
        assertThat(kept.products()).isEmpty();
        assertThat(kept.archived()).isFalse();
    }

    @Test
    void aRenameKeepsTheVersionAndWorksOnAQuotedProject() {
        var owner = viewerFor(newSub());
        var id = newProject(owner);
        var project = projects.find(owner, id).orElseThrow();
        project.lock();
        projects.save(owner, project);

        assertThat(projects.rename(owner, id, "Golem for a buyer")).isTrue();

        var renamed = projects.find(owner, id).orElseThrow();
        assertThat(renamed.name()).isEqualTo("Golem for a buyer");
        assertThat(renamed.version()).isEqualTo(1);
        assertThat(renamed.status()).isEqualTo(ProjectStatus.QUOTED);
    }

    @Test
    void startedFromAPlanRemembersItOnlyWhenThePlanIsTheViewers() {
        var owner = viewerFor(newSub());
        var other = viewerFor(newSub());
        var ownPlan = plans.create(owner, "Source");
        var foreignPlan = plans.create(other, "Not mine");

        var fromOwn = projects.create(owner, "A", ownPlan);
        var fromForeign = projects.create(owner, "B", foreignPlan);

        assertThat(sourcePlanSet(fromOwn)).isTrue();
        assertThat(sourcePlanSet(fromForeign)).isFalse();
    }

    private boolean sourcePlanSet(ProjectId id) {
        return jdbc.sql("SELECT source_plan_id IS NOT NULL FROM project WHERE public_id = :id").param("id", id.value())
                .query(Boolean.class).single();
    }

    @Test
    void aFacilityOfAnotherAccountIsRefusedAndNothingIsSaved() {
        var owner = viewerFor(newSub());
        var other = viewerFor(newSub());
        var foreign = facilityOf(other);
        var id = newProject(owner);
        var asDefault = projects.find(owner, id).orElseThrow();
        asDefault.defaultFacility(foreign);
        var onAnItem = projects.find(owner, id).orElseThrow();
        onAnItem.setItem(new ProjectItem(HAULER, BlueprintSource.TYPED, 10, 20, BigDecimal.ZERO, foreign, null));

        assertThatThrownBy(() -> projects.save(owner, asDefault)).isInstanceOf(FacilityNotFoundException.class);
        assertThatThrownBy(() -> projects.save(owner, onAnItem)).isInstanceOf(FacilityNotFoundException.class);

        var kept = projects.find(owner, id).orElseThrow();
        assertThat(kept.version()).isZero();
        assertThat(kept.config().defaultFacility()).isNull();
        assertThat(kept.items()).isEmpty();
    }

    @Test
    void aLockedProjectIsStoredAsQuotedAndReopensAsADraft() {
        var owner = viewerFor(newSub());
        var id = newProject(owner);
        var project = projects.find(owner, id).orElseThrow();
        project.lock();
        assertThat(projects.save(owner, project)).contains(1L);

        var quoted = projects.find(owner, id).orElseThrow();
        assertThat(quoted.status()).isEqualTo(ProjectStatus.QUOTED);
        quoted.reopen();
        assertThat(projects.save(owner, quoted)).contains(2L);

        assertThat(projects.find(owner, id).orElseThrow().status()).isEqualTo(ProjectStatus.DRAFT);
    }

    @Test
    void archivingIsAStatusAndDoesNotChangeTheVersionOrTheState() {
        var owner = viewerFor(newSub());
        var id = newProject(owner);
        var project = projects.find(owner, id).orElseThrow();
        project.lock();
        projects.save(owner, project);

        assertThat(projects.setArchived(owner, id, true)).isTrue();
        var archived = projects.find(owner, id).orElseThrow();
        assertThat(archived.archived()).isTrue();
        assertThat(archived.version()).isEqualTo(1);
        assertThat(archived.status()).isEqualTo(ProjectStatus.QUOTED);

        assertThat(projects.setArchived(owner, id, false)).isTrue();
        assertThat(projects.find(owner, id).orElseThrow().archived()).isFalse();
    }

    @Test
    void deletingRemovesTheProjectAndEverythingUnderIt() {
        var owner = viewerFor(newSub());
        var id = newProject(owner);
        var project = projects.find(owner, id).orElseThrow();
        project.addProduct(HAULER, 1);
        project.buyPart(COMPONENT);
        project.builder(new ProjectBuilder(0, 0, BigDecimal.ZERO, Map.of(SKILL, 1)));
        project.setItem(ProjectItem.typed(HAULER));
        project.setPrice(TRITANIUM, new PlanPrice(new BigDecimal("3"), null, AS_OF));
        projects.save(owner, project);

        assertThat(projects.delete(owner, id)).isTrue();

        assertThat(projects.find(owner, id)).isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM project_product pp WHERE NOT EXISTS (SELECT 1 FROM project p WHERE p.id = pp.project_id)")
                .query(Integer.class).single()).isZero();
        assertThat(projects.delete(owner, id)).isFalse();
    }

    @Test
    void anItemOverrideOfTheSplitNeedsAStrategyWhenItHasABound() {
        var owner = viewerFor(newSub());
        var id = newProject(owner);
        var project = projects.find(owner, id).orElseThrow();
        project.addProduct(HAULER, 1);
        project.setItem(ProjectItem.typed(HAULER));
        projects.save(owner, project);

        // a bound with no strategy is refused by the table
        assertThatThrownBy(() -> jdbc.sql("UPDATE project_item SET max_job_seconds = 100 WHERE split_strategy IS NULL AND project_id = "
                + "(SELECT id FROM project WHERE public_id = :id)").param("id", id.value()).update())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
