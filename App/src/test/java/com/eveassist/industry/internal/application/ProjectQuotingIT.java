package com.eveassist.industry.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.IntegrationTest;
import com.eveassist.identity.Viewer;
import com.eveassist.industry.FacilityId;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.domain.ProjectLockedException;
import com.eveassist.industry.internal.domain.ProjectStatus;
import com.eveassist.industry.internal.domain.ProjectWarning;
import com.eveassist.industry.internal.domain.ProjectWarning.Kind;
import com.eveassist.market.AdjustedPriceService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Issuing a quote, making a new version from one, and deleting one, on the real tables, with the scenario of {@link TwoHaulers}: costed it
 * is 11,344.984 ISK, and the 20% margin quote rounds up to 15,000. Each test makes its own accounts.
 */
class ProjectQuotingIT extends IntegrationTest {

    @Autowired
    ProjectQuoting quoting;

    @Autowired
    ProjectQuoteQueries queries;

    @Autowired
    ProjectEntry entry;

    @Autowired
    ProjectCostings costings;

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

    private static Instant inAWeek() {
        return Instant.now().plus(Duration.ofDays(7));
    }

    /** Costs the project and issues a quote from it, as the developer would. */
    private QuoteIssued issueQuote(Viewer owner, ProjectId id) {
        entry.cost(owner, id);
        var version = projects.find(owner, id).orElseThrow().version();
        return quoting.issue(owner, new IssueQuoteCommand(id, version, "Buyer", "deliver by contract", inAWeek()));
    }

    private long versionOf(Viewer owner, ProjectId id) {
        return projects.find(owner, id).orElseThrow().version();
    }

    @Test
    void issuingQuotesTheStoredCostingAndLocksTheProject() {
        var owner = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"));
        entry.cost(owner, id);
        var costed = costings.latest(owner, id).orElseThrow();
        var costedTrace = costings.latestTrace(owner, id).orElseThrow();
        var version = versionOf(owner, id);
        // whole seconds, because the database keeps microseconds and rounds a finer time
        var expires = inAWeek().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        var issued = quoting.issue(owner, new IssueQuoteCommand(id, version, "  Buyer ", "deliver by contract", expires));

        assertThat(issued.number()).isEqualTo(1);
        assertThat(issued.projectVersion()).isEqualTo(version + 1);
        var project = projects.find(owner, id).orElseThrow();
        assertThat(project.status()).isEqualTo(ProjectStatus.QUOTED);
        assertThat(project.lastQuoteNumber()).isEqualTo(1);
        assertThat(project.version()).isEqualTo(version + 1);

        var summary = queries.list(owner, id).getFirst();
        assertThat(summary.number()).isEqualTo(1);
        assertThat(summary.buyerName()).isEqualTo("Buyer");
        assertThat(summary.note()).isEqualTo("deliver by contract");
        assertThat(summary.mode()).isEqualTo("margin");
        assertThat(summary.price()).isEqualByComparingTo("15000");
        assertThat(summary.deliveredCost()).isEqualByComparingTo("11344.98");
        assertThat(summary.expiresAt()).isEqualTo(expires);
        // the quote's costing and trace are exactly what was costed, and the Draft has none of its own any more
        assertThat(queries.quote(owner, id, 1).costing().document()).isEqualTo(costed.document());
        assertThat(queries.quote(owner, id, 1).costing().costedProjectVersion()).isEqualTo(costed.costedProjectVersion());
        assertThat(queries.trace(owner, id, 1)).isEqualTo(costedTrace);
        assertThat(costings.latest(owner, id)).isEmpty();
        // and the project is locked
        assertThatThrownBy(() -> editing.changeQuantity(owner, new ChangeProjectQuantityCommand(id, HAULER, 9, version + 1)))
                .isInstanceOf(ProjectLockedException.class);
        assertThatThrownBy(() -> entry.cost(owner, id)).isInstanceOf(ProjectLockedException.class);
    }

    /**
     * A stored example of format 1 of a quote's inputs, written by the application on 2026-11-18 from the two Haulers of this class. A
     * quote issued with it long ago must still be restorable, so it is put in as it is, and a new version is made from it. A change to the
     * records or the mapper that stops it from being restored fails here, and not when someone makes a new version from an old quote.
     */
    @Test
    void aStoredExampleOfFormatOneOfAQuotesInputsStillMakesANewVersion() throws Exception {
        var owner = viewerFor(newSub());
        var facility = haulers.azbel(owner, "2");
        var id = haulers.project(owner, facility);
        var example = new String(getClass().getResourceAsStream("/documents/quote-inputs-v1.json").readAllBytes());
        entry.cost(owner, id);
        var project = projects.find(owner, id).orElseThrow();
        jdbc.sql("""
                INSERT INTO project_quote (project_id, number, mode, price, inputs_format_version, inputs)
                SELECT p.id, 1, 'margin', 15000, 1, CAST(:inputs AS jsonb) FROM project p WHERE p.public_id = :id
                """).param("id", id.value()).param("inputs", example).update();
        jdbc.sql("UPDATE project SET status = 'quoted', last_quote_number = 1 WHERE public_id = :id").param("id", id.value()).update();

        var result = quoting.newVersionFrom(owner, new NewVersionCommand(id, 1, project.version()));

        // the example's facility is not any facility of this account, so it is reported as gone and the rest is restored
        assertThat(result.goneFacilities()).hasSize(1);
        var draft = projects.find(owner, id).orElseThrow();
        assertThat(draft.draft()).isTrue();
        assertThat(draft.products()).extracting(p -> p.type().value(), p -> p.quantity()).containsExactly(org.assertj.core.groups.Tuple.tuple(1001, 2L));
        assertThat(draft.boughtParts()).isEmpty();
        // the stored example is from when the skills were on the items: they are the builder's now
        assertThat(draft.config().builder().skillLevels()).hasSize(2);
        assertThat(draft.prices()).containsOnlyKeys(TRITANIUM, PILOTS_MINERAL);
        assertThat(draft.config().builder().industry()).isEqualTo(5);
        assertThat(draft.config().priceSide()).isEqualTo(com.eveassist.industry.internal.domain.PriceSide.SELL);
    }

    @Test
    void aBuyerOrNoteLeftOutIsTheOneTheDraftHasAndTheQuoteKeepsWhatItWasIssuedWith() {
        var owner = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"));
        var project = projects.find(owner, id).orElseThrow();
        editing.setConfig(owner, new SetProjectConfigCommand(id, project.config().withBuyer("Draft buyer", "draft note"),
                project.version()));
        entry.cost(owner, id);

        quoting.issue(owner, new IssueQuoteCommand(id, versionOf(owner, id), null, "   ", null));

        var summary = queries.list(owner, id).getFirst();
        assertThat(summary.buyerName()).isEqualTo("Draft buyer");
        assertThat(summary.note()).isEqualTo("draft note");
        assertThat(summary.expiresAt()).isNull();
    }

    @Test
    void aQuoteIsRefusedWhenThereIsNoCostingForTheVersionOrItCannotBeQuoted() {
        var owner = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"));
        var version = versionOf(owner, id);

        // never costed
        assertThatThrownBy(() -> quoting.issue(owner, new IssueQuoteCommand(id, version, "B", null, null)))
                .isInstanceOf(CostingOutOfDateException.class);

        // costed, and then edited: the costing is for an earlier version
        entry.cost(owner, id);
        var costedAt = versionOf(owner, id);
        editing.changeQuantity(owner, new ChangeProjectQuantityCommand(id, HAULER, 3, costedAt));
        var edited = versionOf(owner, id);
        assertThatThrownBy(() -> quoting.issue(owner, new IssueQuoteCommand(id, edited, "B", null, null)))
                .isInstanceOf(CostingOutOfDateException.class);

        // what the developer saw is not the version it is at now
        assertThatThrownBy(() -> quoting.issue(owner, new IssueQuoteCommand(id, costedAt, "B", null, null)))
                .isInstanceOf(ProjectChangedException.class);

        // nothing was changed by any of these
        var project = projects.find(owner, id).orElseThrow();
        assertThat(project.draft()).isTrue();
        assertThat(project.lastQuoteNumber()).isZero();
        assertThat(queries.list(owner, id)).isEmpty();
    }

    @Test
    void aCostingWithABlockingWarningCannotBeQuoted() {
        var owner = viewerFor(newSub());
        var id = haulers.project(owner, null);
        var run = entry.cost(owner, id);
        assertThat(run.result().warnings()).contains(new ProjectWarning(Kind.NO_FACILITY, HAULER));

        assertThatThrownBy(() -> quoting.issue(owner, new IssueQuoteCommand(id, versionOf(owner, id), "B", null, null)))
                .isInstanceOf(NotQuotableException.class);
        assertThat(projects.find(owner, id).orElseThrow().draft()).isTrue();
    }

    @Test
    void anExpiryInThePastAndTextThatIsTooLongAreRefused() {
        var owner = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"));
        entry.cost(owner, id);
        var version = versionOf(owner, id);

        assertThatThrownBy(() -> quoting.issue(owner, new IssueQuoteCommand(id, version, "B", null, Instant.now().minusSeconds(60))))
                .isInstanceOf(InvalidQuoteException.class);
        assertThatThrownBy(() -> new IssueQuoteCommand(id, version, "x".repeat(101), null, null))
                .isInstanceOf(InvalidQuoteException.class);
        assertThatThrownBy(() -> new IssueQuoteCommand(id, version, null, "x".repeat(1001), null))
                .isInstanceOf(InvalidQuoteException.class);
        assertThat(projects.find(owner, id).orElseThrow().draft()).isTrue();
    }

    @Test
    void aQuotedProjectCannotBeQuotedAgainAndAnotherAccountsProjectLooksMissing() {
        var owner = viewerFor(newSub());
        var other = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"));
        issueQuote(owner, id);
        var version = versionOf(owner, id);

        assertThatThrownBy(() -> quoting.issue(owner, new IssueQuoteCommand(id, version, "B", null, null)))
                .isInstanceOf(ProjectLockedException.class);
        assertThatThrownBy(() -> quoting.issue(other, new IssueQuoteCommand(id, version, "B", null, null)))
                .isInstanceOf(ProjectNotFoundException.class);
        assertThatThrownBy(() -> queries.list(other, id)).isInstanceOf(ProjectNotFoundException.class);
        assertThatThrownBy(() -> queries.quote(other, id, 1)).isInstanceOf(QuoteNotFoundException.class);
        assertThatThrownBy(() -> queries.trace(other, id, 1)).isInstanceOf(QuoteNotFoundException.class);
    }

    @Test
    void aNewVersionRestoresTheInputsAndCostsExactlyWhatTheQuoteWasCosted() {
        var owner = viewerFor(newSub());
        var facility = haulers.azbel(owner, "2");
        var id = haulers.project(owner, facility);
        var before = projects.find(owner, id).orElseThrow();
        issueQuote(owner, id);
        var quoted = queries.quote(owner, id, 1).costing();

        var result = quoting.newVersionFrom(owner, new NewVersionCommand(id, 1, versionOf(owner, id)));

        assertThat(result.changedFacilities()).isEmpty();
        assertThat(result.goneFacilities()).isEmpty();
        var draft = projects.find(owner, id).orElseThrow();
        assertThat(draft.draft()).isTrue();
        assertThat(draft.version()).isEqualTo(result.project().version());
        assertThat(draft.version()).isGreaterThan(before.version());
        assertThat(draft.lastQuoteNumber()).isEqualTo(1);
        // the inputs are what the quote was issued from, down to the facility, the items' skills and the project's own prices
        assertThat(draft.products()).isEqualTo(before.products());
        assertThat(draft.boughtParts()).isEqualTo(before.boughtParts());
        assertThat(draft.items()).usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(before.items());
        assertThat(draft.prices()).usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(before.prices());
        assertThat(draft.config().defaultFacility()).isEqualTo(facility);
        assertThat(draft.config().builder()).isEqualTo(before.config().builder());
        // it has no costing until it is costed, and costing it gives the quote's number to the digit
        assertThat(costings.latest(owner, id)).isEmpty();
        var again = entry.cost(owner, id).result().cost();
        assertThat(again.deliveredCost()).isEqualByComparingTo(quoted.document().deliveredCost());
        assertThat(again.quote().price()).isEqualByComparingTo("15000");
    }

    @Test
    void theEarlierQuoteStaysAsItWasSentWhenALaterOneIsIssued() {
        var owner = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"));
        issueQuote(owner, id);
        var first = queries.quote(owner, id, 1);
        quoting.newVersionFrom(owner, new NewVersionCommand(id, 1, versionOf(owner, id)));
        editing.changeQuantity(owner, new ChangeProjectQuantityCommand(id, HAULER, 3, versionOf(owner, id)));

        var second = issueQuote(owner, id);

        assertThat(second.number()).isEqualTo(2);
        var secondQuote = queries.quote(owner, id, 2);
        assertThat(secondQuote.quote().price()).isGreaterThan(first.quote().price());
        // the first quote did not move
        assertThat(queries.quote(owner, id, 1)).isEqualTo(first);
        assertThat(queries.list(owner, id)).extracting(ProjectQuoteRepository.QuoteSummary::number).containsExactly(1, 2);
    }

    @Test
    void aFacilityThatChangedAfterTheQuoteIsReportedAndUsedAsItIsNow() {
        var owner = viewerFor(newSub());
        var facility = haulers.azbel(owner, "2");
        var id = haulers.project(owner, facility);
        issueQuote(owner, id);
        var quotedInstall = queries.quote(owner, id, 1).costing().document().lines().get(1).amount();
        jdbc.sql("UPDATE facility SET system_cost_index_percent = 3 WHERE public_id = :id").param("id", facility.value()).update();

        var result = quoting.newVersionFrom(owner, new NewVersionCommand(id, 1, versionOf(owner, id)));

        assertThat(result.changedFacilities()).containsExactly(facility);
        assertThat(result.goneFacilities()).isEmpty();
        assertThat(projects.find(owner, id).orElseThrow().config().defaultFacility()).isEqualTo(facility);
        // costing it again uses the facility as it is now, so the install cost differs from the quote's
        var install = entry.cost(owner, id).result().cost().amount(com.eveassist.industry.internal.domain.CostLine.Component.INSTALL_COST);
        assertThat(install).isGreaterThan(quotedInstall);
        // the quote itself still holds what it was costed with
        assertThat(queries.quote(owner, id, 1).costing().document().lines().get(1).amount()).isEqualByComparingTo(quotedInstall);
    }

    @Test
    void aFacilityThatWasDeletedAfterTheQuoteIsReportedAndCostingSaysItIsMissing() {
        var owner = viewerFor(newSub());
        var facility = haulers.azbel(owner, "2");
        var id = haulers.project(owner, facility);
        issueQuote(owner, id);
        jdbc.sql("DELETE FROM facility WHERE public_id = :id").param("id", facility.value()).update();

        var result = quoting.newVersionFrom(owner, new NewVersionCommand(id, 1, versionOf(owner, id)));

        assertThat(result.goneFacilities()).containsExactly(facility);
        assertThat(projects.find(owner, id).orElseThrow().config().defaultFacility()).isNull();
        assertThat(entry.cost(owner, id).result().warnings()).contains(new ProjectWarning(Kind.NO_FACILITY, HAULER));
    }

    @Test
    void aNewVersionNeedsAQuotedProjectAQuoteThatExistsAndTheVersionTheCallerSaw() {
        var owner = viewerFor(newSub());
        var other = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"));
        assertThatThrownBy(() -> quoting.newVersionFrom(owner, new NewVersionCommand(id, 1, versionOf(owner, id))))
                .isInstanceOf(ProjectNotQuotedException.class);
        issueQuote(owner, id);
        var version = versionOf(owner, id);

        assertThatThrownBy(() -> quoting.newVersionFrom(owner, new NewVersionCommand(id, 9, version)))
                .isInstanceOf(QuoteNotFoundException.class);
        assertThatThrownBy(() -> quoting.newVersionFrom(owner, new NewVersionCommand(id, 1, version - 1)))
                .isInstanceOf(ProjectChangedException.class);
        assertThatThrownBy(() -> quoting.newVersionFrom(other, new NewVersionCommand(id, 1, version)))
                .isInstanceOf(ProjectNotFoundException.class);
        assertThat(projects.find(owner, id).orElseThrow().status()).isEqualTo(ProjectStatus.QUOTED);
    }

    @Test
    void quoteNumbersAreNeverReusedAfterAQuoteIsDeleted() {
        var owner = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"));
        issueQuote(owner, id);
        quoting.newVersionFrom(owner, new NewVersionCommand(id, 1, versionOf(owner, id)));
        assertThat(issueQuote(owner, id).number()).isEqualTo(2);

        var deleted = quoting.deleteQuote(owner, id, 2);

        // quote 1 still stands, so the project stays Quoted
        assertThat(deleted.projectReopened()).isFalse();
        assertThat(projects.find(owner, id).orElseThrow().status()).isEqualTo(ProjectStatus.QUOTED);
        quoting.newVersionFrom(owner, new NewVersionCommand(id, 1, versionOf(owner, id)));
        assertThat(issueQuote(owner, id).number()).as("2 is not used again").isEqualTo(3);
    }

    @Test
    void deletingTheOnlyQuoteOfAQuotedProjectMakesItADraftAgainAndTakesTheCostingAndTrace() {
        var owner = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"));
        issueQuote(owner, id);
        var version = versionOf(owner, id);

        var deleted = quoting.deleteQuote(owner, id, 1);

        assertThat(deleted.projectReopened()).isTrue();
        var project = projects.find(owner, id).orElseThrow();
        assertThat(project.draft()).isTrue();
        assertThat(project.version()).isEqualTo(version + 1);
        assertThat(project.lastQuoteNumber()).isEqualTo(1);
        assertThat(queries.list(owner, id)).isEmpty();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM project_costing c JOIN project p ON p.id = c.project_id WHERE p.public_id = :id
                """).param("id", id.value()).query(Integer.class).single()).isZero();
        // the Draft can be edited and costed again
        editing.changeQuantity(owner, new ChangeProjectQuantityCommand(id, HAULER, 3, project.version()));
    }

    @Test
    void deletingAQuoteThatIsNotThereOrIsAnotherAccountsIsNotFound() {
        var owner = viewerFor(newSub());
        var other = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"));
        issueQuote(owner, id);

        assertThatThrownBy(() -> quoting.deleteQuote(owner, id, 7)).isInstanceOf(QuoteNotFoundException.class);
        assertThatThrownBy(() -> quoting.deleteQuote(other, id, 1)).isInstanceOf(QuoteNotFoundException.class);
        assertThat(queries.list(owner, id)).hasSize(1);
    }
}
