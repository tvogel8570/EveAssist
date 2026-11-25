package com.eveassist.industry.internal.persistence;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.application.CostingDocumentV1;
import com.eveassist.industry.internal.application.ProjectCostingRepository;
import com.eveassist.industry.internal.application.StoredCosting;
import com.eveassist.industry.internal.application.TraceDocumentV1;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Draft's costing and trace as JSON in the tables, written and read by {@link DocumentJson}. Each document is read by its format
 * version, which is a column and not part of the JSON.
 */
@Repository
class JdbcProjectCostingRepository implements ProjectCostingRepository {

    private final JdbcClient jdbc;

    JdbcProjectCostingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public SaveOutcome saveDraft(Viewer viewer, ProjectId id, StoredCosting costing, TraceDocumentV1 trace) {
        record Row(long key, String status) {
        }
        var project = jdbc.sql("SELECT id, status FROM project WHERE public_id = :id AND owner_account_id = :owner")
                .param("id", id.value())
                .param("owner", viewer.accountId().value())
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getString("status")))
                .optional();
        if (project.isEmpty()) {
            return SaveOutcome.NOT_FOUND;
        }
        if (!"draft".equals(project.get().status())) {
            return SaveOutcome.LOCKED;
        }
        var costingId = jdbc.sql("""
                INSERT INTO project_costing (project_id, quote_id, costed_project_version, costed_sde_version, prices_as_of,
                    costed_at, delivered_cost, quotable, document_format_version, document)
                VALUES (:project, NULL, :version, :sde, :pricesAsOf, :costedAt, :delivered, :quotable, :format, CAST(:document AS jsonb))
                ON CONFLICT (project_id) WHERE quote_id IS NULL DO UPDATE SET
                    costed_project_version = excluded.costed_project_version, costed_sde_version = excluded.costed_sde_version,
                    prices_as_of = excluded.prices_as_of, costed_at = excluded.costed_at, delivered_cost = excluded.delivered_cost,
                    quotable = excluded.quotable, document_format_version = excluded.document_format_version,
                    document = excluded.document
                WHERE project_costing.costed_project_version <= excluded.costed_project_version
                RETURNING id
                """)
                .param("project", project.get().key())
                .param("version", costing.costedProjectVersion())
                .param("sde", costing.sdeVersion())
                .param("pricesAsOf", costing.pricesAsOf() == null ? null : Timestamp.from(costing.pricesAsOf()), Types.TIMESTAMP)
                .param("costedAt", Timestamp.from(costing.costedAt()))
                .param("delivered", costing.deliveredCost())
                .param("quotable", costing.quotable())
                .param("format", CostingDocumentV1.FORMAT)
                .param("document", DocumentJson.write(costing.document()))
                .query(Long.class)
                .optional();
        if (costingId.isEmpty()) {
            return SaveOutcome.STALE;
        }
        jdbc.sql("""
                INSERT INTO project_trace (costing_id, document_format_version, document)
                VALUES (:costing, :format, CAST(:document AS jsonb))
                ON CONFLICT (costing_id) DO UPDATE SET document_format_version = excluded.document_format_version,
                    document = excluded.document, created_at = now()
                """)
                .param("costing", costingId.get())
                .param("format", TraceDocumentV1.FORMAT)
                .param("document", DocumentJson.write(trace))
                .update();
        return SaveOutcome.SAVED;
    }

    @Override
    public Optional<StoredCosting> findDraft(Viewer viewer, ProjectId id) {
        return jdbc.sql("""
                SELECT c.costed_project_version, c.costed_sde_version, c.prices_as_of, c.costed_at, c.delivered_cost, c.quotable,
                    c.document_format_version, c.document::text AS document
                FROM project_costing c JOIN project p ON p.id = c.project_id
                WHERE p.public_id = :id AND p.owner_account_id = :owner AND c.quote_id IS NULL
                """)
                .param("id", id.value())
                .param("owner", viewer.accountId().value())
                .query((rs, n) -> new StoredCosting(rs.getLong("costed_project_version"), rs.getString("costed_sde_version"),
                        rs.getTimestamp("prices_as_of") == null ? null : rs.getTimestamp("prices_as_of").toInstant(),
                        rs.getTimestamp("costed_at").toInstant(), rs.getBigDecimal("delivered_cost"), rs.getBoolean("quotable"),
                        DocumentJson.readCosting(rs.getInt("document_format_version"), rs.getString("document"))))
                .optional();
    }

    @Override
    public Optional<TraceDocumentV1> findDraftTrace(Viewer viewer, ProjectId id) {
        return jdbc.sql("""
                SELECT t.document_format_version, t.document::text AS document
                FROM project_trace t JOIN project_costing c ON c.id = t.costing_id JOIN project p ON p.id = c.project_id
                WHERE p.public_id = :id AND p.owner_account_id = :owner AND c.quote_id IS NULL
                """)
                .param("id", id.value())
                .param("owner", viewer.accountId().value())
                .query((rs, n) -> DocumentJson.readTrace(rs.getInt("document_format_version"), rs.getString("document")))
                .optional();
    }
}
