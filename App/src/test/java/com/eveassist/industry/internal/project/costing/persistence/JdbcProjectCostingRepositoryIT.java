package com.eveassist.industry.internal.project.costing.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.IntegrationTest;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.project.costing.application.CostingDocumentV1;
import com.eveassist.industry.internal.project.costing.application.ProjectCostingRepository;
import com.eveassist.industry.internal.project.costing.application.ProjectCostingRepository.SaveOutcome;
import com.eveassist.industry.internal.project.application.ProjectRepository;
import com.eveassist.industry.internal.project.costing.application.StoredCosting;
import com.eveassist.industry.internal.project.costing.application.TraceDocumentV1;
import com.eveassist.industry.internal.project.application.UnreadableDocumentException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The Draft's costing and trace through the repository: what is stored, what a save replaces, which one wins, and whose it is. */
class JdbcProjectCostingRepositoryIT extends IntegrationTest {

    private static final Instant COSTED_AT = Instant.parse("2026-11-18T22:30:00Z");

    @Autowired
    ProjectCostingRepository costings;

    @Autowired
    ProjectRepository projects;

    @Autowired
    JdbcClient jdbc;

    private static CostingDocumentV1 document(long version, String delivered) {
        return new CostingDocumentV1(
                new CostingDocumentV1.Provenance(version, "test-sde", COSTED_AT, Instant.parse("2026-11-18T12:00:00Z"),
                        Instant.parse("2026-11-18T22:29:00Z"), Instant.parse("2026-11-18T22:42:41Z")),
                List.of(new CostingDocumentV1.Line("MATERIALS", new BigDecimal("1255")),
                        new CostingDocumentV1.Line("SURPLUS_CREDIT", new BigDecimal("-320.00"))),
                new BigDecimal(delivered), new BigDecimal("5672.492000"), 9886, 5222, true,
                new CostingDocumentV1.Quote("margin", new BigDecimal("15000"), new BigDecimal("7500"), new BigDecimal("3655.016"),
                        new BigDecimal("24.36677333")),
                null,
                List.of(new CostingDocumentV1.Job(1001, 2, 2, 4664, new BigDecimal("1200"), new BigDecimal("71.04"), BigDecimal.ZERO,
                        "typed", List.of(new CostingDocumentV1.Material(1002, 4), new CostingDocumentV1.Material(34, 179)))),
                List.of(new CostingDocumentV1.Bought(34, 215, new BigDecimal("5.00"), new BigDecimal("1075.00"))),
                List.of(new CostingDocumentV1.Warning("uses_original", 1001)),
                new CostingDocumentV1.Split(new BigDecimal("11339.984"), new BigDecimal("5"),
                        List.of(new CostingDocumentV1.Line("MATERIALS", new BigDecimal("5"))), 0, 2611, null));
    }

    private static TraceDocumentV1 trace(long version) {
        var facility = new TraceDocumentV1.Facility(new BigDecimal("1"), BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO,
                new BigDecimal("4"), new BigDecimal("2.002122"), BigDecimal.ZERO, new BigDecimal("4"));
        var job = new TraceDocumentV1.Job(1001, 2, 1, "typed", 10, 20, facility,
                new TraceDocumentV1.Builder(5, 5, new BigDecimal("4"), List.of(new TraceDocumentV1.Skill(5, BigDecimal.ONE))),
                List.of(new TraceDocumentV1.Material(34, 100, new BigDecimal("178.2"), new BigDecimal("178.20"), 179, false)),
                new TraceDocumentV1.Time(6000, new BigDecimal("0.80"), new BigDecimal("0.60724"), new BigDecimal("0.80"), BigDecimal.ONE,
                        new BigDecimal("4663.6032"), 4664, 5551),
                new TraceDocumentV1.Install(new BigDecimal("1200"), new BigDecimal("2.002122"), new BigDecimal("24.025464"),
                        new BigDecimal("-0.96101"), new BigDecimal("23.064454"), BigDecimal.ZERO, new BigDecimal("48"),
                        new BigDecimal("71.064454")),
                List.of(new TraceDocumentV1.EivLine(34, 100, new BigDecimal("2.9139375060564285")),
                        new TraceDocumentV1.EivLine(36, 1, null)));
        return new TraceDocumentV1(new TraceDocumentV1.Data("abc1234", "test-sde", COSTED_AT, version),
                new TraceDocumentV1.Inputs("sell", BigDecimal.ZERO, new BigDecimal("300"), new BigDecimal("10000"), "margin",
                        new BigDecimal("20"), BigDecimal.ZERO, new BigDecimal("1000"), "least_cost", null, 5, 5, new BigDecimal("4")),
                List.of(new TraceDocumentV1.Demand(1001, 2, 2, 1, 2)), List.of(job),
                List.of(new TraceDocumentV1.UnitPrice(34, new BigDecimal("5"), null, Instant.parse("2026-11-18T12:00:00Z"))));
    }

    private static StoredCosting stored(long version, String delivered) {
        return new StoredCosting(version, "test-sde", Instant.parse("2026-11-18T12:00:00Z"), COSTED_AT, new BigDecimal(delivered),
                true, document(version, delivered));
    }

    private ProjectId newProject(com.eveassist.identity.Viewer owner) {
        return projects.create(owner, "Mine", null);
    }

    @Test
    void storesAndReadsBackTheCostingAndTheTraceExactly() {
        var owner = viewerFor(newSub());
        var id = newProject(owner);

        assertThat(costings.saveDraft(owner, id, stored(3, "11344.984"), trace(3))).isEqualTo(SaveOutcome.SAVED);

        var read = costings.findDraft(owner, id).orElseThrow();
        assertThat(read.costedProjectVersion()).isEqualTo(3);
        assertThat(read.sdeVersion()).isEqualTo("test-sde");
        // the column is for lists and holds the cost to the cent. The exact amount is in the document
        assertThat(read.deliveredCost()).isEqualByComparingTo("11344.98");
        assertThat(read.document().deliveredCost()).isEqualByComparingTo("11344.984");
        assertThat(read.quotable()).isTrue();
        // the documents come back as they went in: digits and scale, nulls, and times
        assertThat(read.document()).isEqualTo(document(3, "11344.984"));
        assertThat(costings.findDraftTrace(owner, id)).contains(trace(3));
    }

    @Test
    void numbersAreStoredInFullAndNeverAsAnExponent() {
        var owner = viewerFor(newSub());
        var id = newProject(owner);
        var huge = new BigDecimal("122413205771.6132");
        var exponent = BigDecimal.valueOf(1, -3);   // 1E+3

        var doc = document(1, "1");
        costings.saveDraft(owner, id, new StoredCosting(1, "test-sde", null, COSTED_AT, huge, true,
                new CostingDocumentV1(doc.provenance(), doc.lines(), exponent, huge, 0, 0, true, null, null, doc.jobs(), doc.bought(),
                        doc.warnings(), doc.split())), trace(1));

        var text = jdbc.sql("SELECT c.document::text FROM project_costing c JOIN project p ON p.id = c.project_id WHERE p.public_id = :id")
                .param("id", id.value()).query(String.class).single();
        assertThat(text).doesNotContain("E+").contains("122413205771.6132");
        assertThat(costings.findDraft(owner, id).orElseThrow().document().deliveredCost()).isEqualByComparingTo("1000");
    }

    @Test
    void aSecondCostingReplacesTheFirstAndItsTraceAndThereIsStillOneOfEach() {
        var owner = viewerFor(newSub());
        var id = newProject(owner);
        costings.saveDraft(owner, id, stored(3, "100"), trace(3));

        assertThat(costings.saveDraft(owner, id, stored(4, "200"), trace(4))).isEqualTo(SaveOutcome.SAVED);

        assertThat(costings.findDraft(owner, id).orElseThrow().deliveredCost()).isEqualByComparingTo("200");
        assertThat(costings.findDraftTrace(owner, id).orElseThrow().data().costedProjectVersion()).isEqualTo(4);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM project_costing c JOIN project p ON p.id = c.project_id WHERE p.public_id = :id
                """).param("id", id.value()).query(Integer.class).single()).isOne();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM project_trace t JOIN project_costing c ON c.id = t.costing_id
                JOIN project p ON p.id = c.project_id WHERE p.public_id = :id
                """).param("id", id.value()).query(Integer.class).single()).isOne();
    }

    @Test
    void aCostingForAnEarlierEditCannotOverwriteALaterOneButTheSameEditCan() {
        var owner = viewerFor(newSub());
        var id = newProject(owner);
        costings.saveDraft(owner, id, stored(5, "500"), trace(5));

        assertThat(costings.saveDraft(owner, id, stored(4, "400"), trace(4))).isEqualTo(SaveOutcome.STALE);
        assertThat(costings.findDraft(owner, id).orElseThrow().deliveredCost()).isEqualByComparingTo("500");
        assertThat(costings.findDraftTrace(owner, id).orElseThrow().data().costedProjectVersion()).isEqualTo(5);

        // costing again at the same edit, with newer prices, replaces it
        assertThat(costings.saveDraft(owner, id, stored(5, "510"), trace(5))).isEqualTo(SaveOutcome.SAVED);
        assertThat(costings.findDraft(owner, id).orElseThrow().deliveredCost()).isEqualByComparingTo("510");
    }

    @Test
    void anotherAccountsProjectLooksMissingAndAQuotedProjectIsLocked() {
        var owner = viewerFor(newSub());
        var other = viewerFor(newSub());
        var id = newProject(owner);
        costings.saveDraft(owner, id, stored(1, "100"), trace(1));

        assertThat(costings.saveDraft(other, id, stored(2, "999"), trace(2))).isEqualTo(SaveOutcome.NOT_FOUND);
        assertThat(costings.findDraft(other, id)).isEmpty();
        assertThat(costings.findDraftTrace(other, id)).isEmpty();
        assertThat(costings.findDraft(owner, id).orElseThrow().deliveredCost()).isEqualByComparingTo("100");

        var project = projects.find(owner, id).orElseThrow();
        project.lock();
        projects.save(owner, project);
        assertThat(costings.saveDraft(owner, id, stored(9, "900"), trace(9))).isEqualTo(SaveOutcome.LOCKED);
    }

    @Test
    void aProjectNeverCostedHasNoCosting() {
        var owner = viewerFor(newSub());

        assertThat(costings.findDraft(owner, newProject(owner))).isEmpty();
    }

    @Test
    void aDocumentOfAFormatNoReaderKnowsIsRefusedByName() {
        var owner = viewerFor(newSub());
        var id = newProject(owner);
        costings.saveDraft(owner, id, stored(1, "100"), trace(1));
        jdbc.sql("""
                UPDATE project_costing SET document_format_version = 9 WHERE project_id = (SELECT id FROM project WHERE public_id = :id)
                """).param("id", id.value()).update();
        jdbc.sql("""
                UPDATE project_trace SET document_format_version = 9 WHERE costing_id IN
                    (SELECT c.id FROM project_costing c JOIN project p ON p.id = c.project_id WHERE p.public_id = :id)
                """).param("id", id.value()).update();

        assertThatThrownBy(() -> costings.findDraft(owner, id)).isInstanceOf(UnreadableDocumentException.class)
                .hasMessageContaining("format 9");
        assertThatThrownBy(() -> costings.findDraftTrace(owner, id)).isInstanceOf(UnreadableDocumentException.class)
                .hasMessageContaining("format 9");
    }

    /**
     * A stored example of format 1, written by the application on 2026-11-18 from two Haulers in an Azbel (the numbers of
     * ProjectCostingsIT). It is read through the repository as if it had been stored long ago, so a change to the document records that
     * would stop old documents from being read fails here, and not when someone opens an old quote. A change that alters what a field
     * means is a new format beside this one, and this example stays for as long as a document of format 1 can be stored.
     */
    @Test
    void aStoredExampleOfFormatOneStillReads() throws Exception {
        var owner = viewerFor(newSub());
        var id = newProject(owner);
        var costing = new String(getClass().getResourceAsStream("/documents/costing-document-v1.json").readAllBytes());
        var trace = new String(getClass().getResourceAsStream("/documents/trace-document-v1.json").readAllBytes());
        var costingId = jdbc.sql("""
                INSERT INTO project_costing (project_id, quote_id, costed_project_version, costed_sde_version, costed_at, delivered_cost,
                    quotable, document_format_version, document)
                SELECT p.id, NULL, 5, 'test-sde', now(), 11344.98, true, 1, CAST(:document AS jsonb) FROM project p WHERE p.public_id = :id
                RETURNING id
                """).param("id", id.value()).param("document", costing).query(Long.class).single();
        jdbc.sql("INSERT INTO project_trace (costing_id, document_format_version, document) VALUES (:c, 1, CAST(:document AS jsonb))")
                .param("c", costingId).param("document", trace).update();

        var read = costings.findDraft(owner, id).orElseThrow().document();
        assertThat(read.provenance().sdeVersion()).isEqualTo("test-sde");
        assertThat(read.provenance().adjustedPricesEsiModifiedAt()).isEqualTo(Instant.parse("2026-11-18T20:41:10Z"));
        assertThat(read.lines()).hasSize(7);
        assertThat(read.lines().getFirst().component()).isEqualTo("materials");
        assertThat(read.lines().getFirst().amount()).isEqualByComparingTo("1255");
        assertThat(read.deliveredCost()).isEqualByComparingTo("11344.984");
        assertThat(read.quote().price()).isEqualByComparingTo("15000");
        assertThat(read.jobs()).hasSize(2);
        assertThat(read.bought()).hasSize(2);
        assertThat(read.split().longestJobSecondsSaved()).isZero();
        var readTrace = costings.findDraftTrace(owner, id).orElseThrow();
        assertThat(readTrace.data().appCommit()).isEqualTo("dev");
        var hauler = readTrace.jobs().stream().filter(job -> job.product() == 1001).findFirst().orElseThrow();
        assertThat(hauler.install().total()).isEqualByComparingTo("71.04");
        assertThat(hauler.time().exactSeconds()).isEqualByComparingTo("4663.6032");
        assertThat(hauler.materials()).extracting(TraceDocumentV1.Material::quantity).containsExactlyInAnyOrder(4L, 179L);
    }

    @Test
    void aDocumentWithAFieldThisVersionDoesNotKnowIsStillRead() {
        var owner = viewerFor(newSub());
        var id = newProject(owner);
        costings.saveDraft(owner, id, stored(1, "100"), trace(1));
        // a later document of the same format may have gained a field: it is ignored and the rest is read
        jdbc.sql("""
                UPDATE project_costing SET document = jsonb_set(document, '{aFieldAddedLater}', '"x"') WHERE project_id =
                    (SELECT id FROM project WHERE public_id = :id)
                """).param("id", id.value()).update();

        assertThat(costings.findDraft(owner, id).orElseThrow().document()).isEqualTo(document(1, "100"));
    }
}
