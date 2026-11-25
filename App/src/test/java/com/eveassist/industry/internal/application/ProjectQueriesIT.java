package com.eveassist.industry.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.IntegrationTest;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.domain.ProjectStatus;
import com.eveassist.market.AdjustedPriceService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * What a project's pages read, on the real tables and the scenario of {@link TwoHaulers}: a list that shows each project's stored cost and
 * whether it may be out of date, and a page that shows the project with names, its items and skills, the facilities, the quotes and the costing.
 */
class ProjectQueriesIT extends IntegrationTest {

    @Autowired
    ProjectQueries queries;

    @Autowired
    ProjectQuoting quoting;

    @Autowired
    ProjectEntry entry;

    @Autowired
    ProjectEditing editing;

    @Autowired
    ProjectRepository projects;

    @Autowired
    PlanRepository plans;

    @Autowired
    AdjustedPriceService adjusted;

    @Autowired
    JdbcClient jdbc;

    private TwoHaulers haulers;

    @BeforeEach
    void adjustedPricesAndScenario() {
        haulers = new TwoHaulers(editing, projects, jdbc);
        esi.adjustedPrice(TRITANIUM.value(), "4");
        esi.adjustedPrice(PILOTS_MINERAL.value(), "8");
        esi.adjustedPrice(COMPONENT.value(), "100");
        adjusted.refresh();
    }

    private long versionOf(com.eveassist.identity.Viewer owner, ProjectId id) {
        return projects.find(owner, id).orElseThrow().version();
    }

    private ProjectOverview row(com.eveassist.identity.Viewer owner, ProjectId id, boolean archived) {
        return queries.overviews(owner, archived).stream().filter(overview -> overview.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void theListShowsEachProjectsStoredCostAndWhetherItIsOutOfDate() {
        var owner = viewerFor(newSub());
        var facility = haulers.azbel(owner, "2");
        var drafted = haulers.project(owner, facility, "A drafted", null);
        var quoted = haulers.project(owner, facility, "B quoted", null);
        var archived = haulers.project(owner, facility, "C archived", null);
        var neverCosted = haulers.project(owner, facility, "D never costed", null);
        entry.cost(owner, drafted);
        entry.cost(owner, quoted);
        quoting.issue(owner, new IssueQuoteCommand(quoted, versionOf(owner, quoted), "Buyer", null, Instant.now().plus(Duration.ofDays(7))));
        editing.archive(owner, archived);

        var list = queries.overviews(owner);

        assertThat(list).extracting(ProjectOverview::name).containsExactly("A drafted", "B quoted", "D never costed");
        var a = list.get(0);
        assertThat(a.status()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(a.productCount()).isEqualTo(1);
        assertThat(a.quoteCount()).isZero();
        assertThat(a.deliveredCost()).isEqualByComparingTo("11344.98");
        assertThat(a.costedAt()).isNotNull();
        assertThat(a.outOfDate()).isFalse();
        assertThat(a.latestQuoteNumber()).isNull();
        var b = list.get(1);
        assertThat(b.status()).isEqualTo(ProjectStatus.QUOTED);
        assertThat(b.quoteCount()).isEqualTo(1);
        assertThat(b.deliveredCost()).isEqualByComparingTo("11344.98");
        assertThat(b.latestQuoteNumber()).isEqualTo(1);
        assertThat(b.latestQuotePrice()).isEqualByComparingTo("15000");
        assertThat(b.latestQuoteExpiresAt()).isNotNull();
        assertThat(b.outOfDate()).as("a frozen quote is never out of date").isFalse();
        var d = list.get(2);
        assertThat(d.deliveredCost()).isNull();
        assertThat(d.costedAt()).isNull();
        assertThat(d.outOfDate()).isFalse();
        // the archived one comes after the others when asked for
        assertThat(queries.overviews(owner, true)).extracting(ProjectOverview::name)
                .containsExactly("A drafted", "B quoted", "D never costed", "C archived");
        assertThat(row(owner, archived, true).archived()).isTrue();
    }

    @Test
    void aProjectMadeADraftAgainDoesNotShowTheQuotesCostAsItsOwn() {
        var owner = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"));
        entry.cost(owner, id);
        quoting.issue(owner, new IssueQuoteCommand(id, versionOf(owner, id), "Buyer", null, null));
        quoting.newVersionFrom(owner, new NewVersionCommand(id, 1, versionOf(owner, id)));

        var overview = row(owner, id, false);

        assertThat(overview.status()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(overview.deliveredCost()).isNull();
        assertThat(overview.quoteCount()).isEqualTo(1);
        assertThat(overview.latestQuoteNumber()).isEqualTo(1);
        assertThat(overview.outOfDate()).isFalse();
    }

    @Test
    void aDraftsCostingIsOutOfDateAfterAnEditAnSdeImportOrAChangeToAFacilityItUses() {
        var owner = viewerFor(newSub());
        var facility = haulers.azbel(owner, "2");
        var unrelated = haulers.azbel(owner, "3");
        var id = haulers.project(owner, facility);
        entry.cost(owner, id);
        assertThat(row(owner, id, false).outOfDate()).isFalse();

        // a facility this project does not use changes: nothing to say
        jdbc.sql("UPDATE facility SET updated_at = now() + interval '1 minute' WHERE public_id = :id").param("id", unrelated.value()).update();
        assertThat(row(owner, id, false).outOfDate()).isFalse();
        assertThat(queries.details(owner, id).costing().outOfDate()).isEmpty();

        // the one it uses changes
        jdbc.sql("UPDATE facility SET updated_at = now() + interval '1 minute' WHERE public_id = :id").param("id", facility.value()).update();
        assertThat(row(owner, id, false).outOfDate()).isTrue();
        assertThat(queries.details(owner, id).costing().outOfDate()).containsExactly(CostingStaleness.FACILITY_CHANGED);

        // costed again after that, but not after the clock the change was dated with: bring the change back so only the next reason is seen
        jdbc.sql("UPDATE facility SET updated_at = now() - interval '1 hour' WHERE public_id = :id").param("id", facility.value()).update();
        jdbc.sql("UPDATE project_costing SET costed_sde_version = 'an-older-sde' WHERE project_id = (SELECT id FROM project WHERE public_id = :id)")
                .param("id", id.value()).update();
        assertThat(row(owner, id, false).outOfDate()).isTrue();
        assertThat(queries.details(owner, id).costing().outOfDate()).containsExactly(CostingStaleness.SDE_CHANGED);

        entry.cost(owner, id);
        assertThat(row(owner, id, false).outOfDate()).isFalse();

        // an edit
        editing.changeQuantity(owner, new ChangeProjectQuantityCommand(id, HAULER, 3, versionOf(owner, id)));
        assertThat(row(owner, id, false).outOfDate()).isTrue();
        assertThat(queries.details(owner, id).costing().outOfDate()).containsExactly(CostingStaleness.PROJECT_EDITED);
    }

    @Test
    void theDetailsOfACostedDraftHaveEverythingThePageShows() {
        var owner = viewerFor(newSub());
        var planId = plans.create(owner, "Hauler plan");
        var facility = haulers.azbel(owner, "2");
        var id = haulers.project(owner, facility, "Hauler for a buyer", planId);
        entry.cost(owner, id);

        var details = queries.details(owner, id);

        assertThat(details.name()).isEqualTo("Hauler for a buyer");
        assertThat(details.status()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(details.version()).isEqualTo(versionOf(owner, id));
        assertThat(details.sourcePlanName()).isEqualTo("Hauler plan");
        assertThat(details.startedAt()).isNotNull();
        assertThat(details.config().defaultFacility()).isEqualTo(facility);
        assertThat(details.products()).singleElement().satisfies(product -> {
            assertThat(product.name()).isEqualTo("Test Hauler");
            assertThat(product.quantity()).isEqualTo(2);
        });
        // the items the project builds, the products first, with how each is made. The Component was never set, so it is shown as it will be costed
        assertThat(details.items()).extracting(ProjectDetails.ItemLine::name, ProjectDetails.ItemLine::product,
                ProjectDetails.ItemLine::configured).containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Test Hauler", true, true),
                        org.assertj.core.groups.Tuple.tuple("Test Component", false, false));
        var hauler = details.items().getFirst();
        assertThat(hauler.me()).isEqualTo(10);
        assertThat(hauler.te()).isEqualTo(20);
        assertThat(hauler.skills()).extracting(ProjectDetails.SkillLine::name, ProjectDetails.SkillLine::level,
                skill -> skill.percentPerLevel().intValue()).containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Nanite Engineering", 5, 1),
                        org.assertj.core.groups.Tuple.tuple("Mechanical Engineering", 3, 2));
        var component = details.items().get(1);
        assertThat(component.me()).isEqualTo(10);
        assertThat(component.facility()).isNull();
        assertThat(component.skills()).isEmpty();
        assertThat(details.parts()).extracting(ProjectDetails.PartLine::name, ProjectDetails.PartLine::bought)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Test Component", false));
        assertThat(details.facilities()).extracting(FacilityOverview::id).containsExactly(facility);
        assertThat(details.quotes()).isEmpty();
        // the costing is the stored one, current, with both ages of the adjusted prices
        var costing = details.costing();
        assertThat(costing.frozen()).isFalse();
        assertThat(costing.current()).isTrue();
        assertThat(costing.quoteNumber()).isZero();
        assertThat(costing.costing().document().deliveredCost()).isEqualByComparingTo("11344.984");
        assertThat(costing.costing().document().provenance().adjustedPricesEsiModifiedAt()).isNotNull();
        assertThat(costing.costing().document().quote().price()).isEqualByComparingTo("15000");
    }

    @Test
    void theDetailsOfAQuotedProjectShowTheLatestQuotesFrozenCosting() {
        var owner = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"));
        entry.cost(owner, id);
        quoting.issue(owner, new IssueQuoteCommand(id, versionOf(owner, id), "Buyer", "note", null));

        var details = queries.details(owner, id);

        assertThat(details.status()).isEqualTo(ProjectStatus.QUOTED);
        assertThat(details.lastQuoteNumber()).isEqualTo(1);
        assertThat(details.quotes()).extracting(ProjectQuoteRepository.QuoteSummary::buyerName).containsExactly("Buyer");
        assertThat(details.costing().frozen()).isTrue();
        assertThat(details.costing().quoteNumber()).isEqualTo(1);
        assertThat(details.costing().current()).isTrue();
        assertThat(details.costing().costing().document().quote().price()).isEqualByComparingTo("15000");
        // editing it would be refused, and the page can say what is quoted without costing anything
        assertThat(details.items()).isNotEmpty();
    }

    @Test
    void aProjectThatWasNeverCostedHasNoCostingAndABoughtPartIsShownAsBought() {
        var owner = viewerFor(newSub());
        var id = haulers.project(owner, null);
        editing.choosePartSource(owner, new ChooseProjectPartSourceCommand(id, COMPONENT, true, versionOf(owner, id)));

        var details = queries.details(owner, id);

        assertThat(details.costing()).isNull();
        assertThat(details.parts()).extracting(ProjectDetails.PartLine::name, ProjectDetails.PartLine::bought)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Test Component", true));
        // a bought part is not built, so it has no item of its own to set
        assertThat(details.items()).extracting(ProjectDetails.ItemLine::name).containsExactly("Test Hauler");
        assertThat(details.sourcePlanName()).isNull();
    }

    @Test
    void anotherAccountSeesNeitherTheProjectNorItsRowNorItsFacilities() {
        var owner = viewerFor(newSub());
        var other = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"), "Secret", null);

        assertThatThrownBy(() -> queries.details(other, id)).isInstanceOf(ProjectNotFoundException.class);
        assertThat(queries.overviews(other, true)).isEmpty();
        assertThat(queries.facilities(other)).isEmpty();
        assertThat(queries.facilities(owner)).hasSize(1);
    }

    @Test
    void productsCanBeSearchedByNameForAddingOne() {
        var owner = viewerFor(newSub());

        assertThat(queries.searchProducts(owner, "hauler")).extracting(item -> item.name()).containsExactly("Test Hauler");
        assertThat(queries.searchProducts(owner, "")).isEmpty();
    }
}
