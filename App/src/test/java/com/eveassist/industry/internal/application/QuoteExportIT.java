package com.eveassist.industry.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.IntegrationTest;
import com.eveassist.identity.Viewer;
import com.eveassist.industry.ProjectId;
import com.eveassist.market.AdjustedPriceService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The text and the CSV of an issued quote, on the real tables, with the scenario of {@link TwoHaulers}: two Test Haulers whose 20% margin quote
 * rounds up to 15,000 ISK.
 */
class QuoteExportIT extends IntegrationTest {

    @Autowired
    QuoteExport export;

    @Autowired
    ProjectQuoting quoting;

    @Autowired
    ProjectEntry entry;

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

    private Instant inAWeek() {
        return Instant.now().plus(Duration.ofDays(7)).truncatedTo(ChronoUnit.SECONDS);
    }

    private ProjectId quoted(Viewer owner, String name, String buyer, String note, Instant expires) {
        var id = haulers.project(owner, haulers.azbel(owner, "2"), name, null);
        entry.cost(owner, id);
        var version = projects.find(owner, id).orElseThrow().version();
        quoting.issue(owner, new IssueQuoteCommand(id, version, buyer, note, expires));
        return id;
    }

    @Test
    void anIssuedQuoteIsExportedAsTheTextAndTheCsvOfWhatWasQuoted() {
        var owner = viewerFor(newSub());
        // the page stores a valid-until day as the start of the day after
        var day = LocalDate.now(ZoneOffset.UTC).plusDays(7);
        var id = quoted(owner, "Two Haulers!", "Kestrel Dawn", "Deliver by contract", day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());

        var shareable = export.of(owner, id, 1);

        assertThat(shareable.text()).isEqualTo("""
                Quote 1 for Kestrel Dawn
                2 × Test Hauler, delivered by contract
                Total: 15,000 ISK
                Per unit: 7,500 ISK
                Valid until %s
                Deliver by contract""".formatted(day));
        assertThat(shareable.csv()).contains("1,Kestrel Dawn,Test Hauler,2,,%s,Deliver by contract\r\n".formatted(day))
                .contains("1,Kestrel Dawn,Total,,15000,%s,Deliver by contract\r\n".formatted(day));
        assertThat(shareable.fileName()).isEqualTo("quote-two-haulers-1.csv");
        // never a word of what it cost
        assertThat(shareable.text() + shareable.csv()).doesNotContainIgnoringCase("cost").doesNotContainIgnoringCase("margin")
                .doesNotContainIgnoringCase("profit");
    }

    @Test
    void aQuoteWithNoBuyerNoNoteAndNoExpiryLeavesThemOut() {
        var owner = viewerFor(newSub());
        var id = quoted(owner, "Two Haulers", null, null, null);

        var shareable = export.of(owner, id, 1);

        assertThat(shareable.text()).isEqualTo("""
                Quote 1
                2 × Test Hauler, delivered by contract
                Total: 15,000 ISK
                Per unit: 7,500 ISK""");
    }

    @Test
    void theExportDoesNotChangeWhenTheProjectIsRenamedOrItsFacilityChanges() {
        var owner = viewerFor(newSub());
        var id = quoted(owner, "Two Haulers", "Buyer", null, inAWeek());
        var before = export.of(owner, id, 1);

        editing.rename(owner, new RenameProjectCommand(id, "Something else"));
        jdbc.sql("UPDATE facility SET system_cost_index_percent = 9, updated_at = now() WHERE owner_account_id = :owner")
                .param("owner", owner.accountId().value()).update();

        var after = export.of(owner, id, 1);
        // the text and the CSV are the quote as it was frozen. Only the name of the file follows the project's name
        assertThat(after.text()).isEqualTo(before.text());
        assertThat(after.csv()).isEqualTo(before.csv());
        assertThat(after.fileName()).isEqualTo("quote-something-else-1.csv");
    }

    @Test
    void aQuoteThatDoesNotExistAndAnotherAccountsQuoteLookMissing() {
        var owner = viewerFor(newSub());
        var id = quoted(owner, "Two Haulers", "Buyer", null, inAWeek());

        assertThatThrownBy(() -> export.of(owner, id, 2)).isInstanceOf(QuoteNotFoundException.class);
        assertThatThrownBy(() -> export.of(viewerFor(newSub()), id, 1)).isInstanceOf(ProjectNotFoundException.class);
    }
}
