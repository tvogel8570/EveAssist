package com.eveassist.industry.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.IntegrationTest;
import com.eveassist.market.AdjustedPriceService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Which costing the comparison views show, on the real tables, with the scenario of {@link TwoHaulers}. */
class ProjectComparisonIT extends IntegrationTest {

    @Autowired
    ProjectComparison comparison;

    @Autowired
    ProjectEntry entry;

    @Autowired
    ProjectQuoting quoting;

    @Autowired
    ProjectEditing editing;

    @Autowired
    ProjectRepository projects;

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

    @Test
    void aCostedDraftShowsItsLatestCostingJobByJobWithTheNamesOfWhatItUses() {
        var owner = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"), "Two Haulers", null);
        entry.cost(owner, id);

        var view = comparison.of(owner, id, null);

        assertThat(view.source()).isEqualTo(CompareView.Source.DRAFT);
        assertThat(view.projectName()).isEqualTo("Two Haulers");
        assertThat(view.costedAt()).isNotNull();
        assertThat(view.key()).isEqualTo("draft-" + view.costedAt().toEpochMilli());
        assertThat(view.outOfDate()).isFalse();
        assertThat(view.jobs()).isNotEmpty();
        var hauler = view.jobs().stream().filter(job -> job.product().equals("Test Hauler")).findFirst().orElseThrow();
        assertThat(hauler.runs()).isEqualTo(2);
        assertThat(hauler.unitsMade()).isEqualTo(2);
        assertThat(hauler.materials()).extracting(CompareView.Material::name).contains("Tritanium");
        // the install cost is the one the costing stored, rounded as the window does
        assertThat(hauler.cost().total()).isEqualTo(hauler.cost().gross() + hauler.cost().taxes());
        assertThat(hauler.cost().exactTotal()).isNotNull();
        assertThat(hauler.time().seconds()).isPositive();
    }

    @Test
    void aProjectEditedAfterItWasCostedIsMarkedOutOfDateAndANewCostingIsANewKey() throws Exception {
        var owner = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"), "Two Haulers", null);
        entry.cost(owner, id);
        var first = comparison.of(owner, id, null);

        var version = projects.find(owner, id).orElseThrow().version();
        var config = projects.find(owner, id).orElseThrow().config();
        editing.setConfig(owner, new SetProjectConfigCommand(id, config.withSettings(new com.eveassist.industry.internal.domain.ProjectSettings(
                new BigDecimal("1"), config.settings().iskPerCubicMeter(), config.settings().contractFee(), config.settings().mode(),
                config.settings().marginPercent(), config.settings().premiumPercent(), config.settings().quoteStep())), version));
        assertThat(comparison.of(owner, id, null).outOfDate()).isTrue();

        Thread.sleep(5);
        entry.cost(owner, id);
        var second = comparison.of(owner, id, null);
        assertThat(second.outOfDate()).isFalse();
        assertThat(second.key()).isNotEqualTo(first.key());
    }

    @Test
    void aQuotedProjectShowsItsLatestQuotesFrozenCostingAndAnyQuoteCanBeAsked() {
        var owner = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"), "Two Haulers", null);
        entry.cost(owner, id);
        quoting.issue(owner, new IssueQuoteCommand(id, projects.find(owner, id).orElseThrow().version(), "Buyer", null,
                Instant.now().plus(Duration.ofDays(7))));

        var latest = comparison.of(owner, id, null);
        var byNumber = comparison.of(owner, id, 1);

        assertThat(latest.source()).isEqualTo(CompareView.Source.QUOTE);
        assertThat(latest.quoteNumber()).isEqualTo(1);
        assertThat(latest.key()).isEqualTo("quote-1");
        assertThat(latest.outOfDate()).isFalse();
        assertThat(latest.jobs()).isNotEmpty();
        assertThat(byNumber.jobs()).hasSameSizeAs(latest.jobs());
        assertThatThrownBy(() -> comparison.of(owner, id, 2)).isInstanceOf(QuoteNotFoundException.class);
    }

    @Test
    void aProjectThatIsNotCostedHasNoJobsAndAnotherAccountsProjectLooksMissing() {
        var owner = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"), "Two Haulers", null);

        var view = comparison.of(owner, id, null);

        assertThat(view.costedAt()).isNull();
        assertThat(view.jobs()).isEmpty();
        assertThat(view.projectName()).isEqualTo("Two Haulers");
        assertThatThrownBy(() -> comparison.of(viewerFor(newSub()), id, null)).isInstanceOf(ProjectNotFoundException.class);
    }
}
