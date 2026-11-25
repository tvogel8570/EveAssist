package com.eveassist.industry.internal.persistence;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.application.CostingOutOfDateException;
import com.eveassist.industry.internal.application.ProjectQuoteRepository;
import com.eveassist.industry.internal.application.QuoteInputsV1;
import com.eveassist.industry.internal.application.StoredCosting;
import com.eveassist.industry.internal.application.TraceDocumentV1;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The quotes in their tables. A quote is never updated (the database refuses it), so this class only inserts and deletes them. The
 * documents are read by their format version, which is a column ({@link DocumentJson}).
 */
@Repository
class JdbcProjectQuoteRepository implements ProjectQuoteRepository {

    private static final String SUMMARY = """
            SELECT q.number, q.issued_at, q.expires_at, q.buyer_name, q.note, q.mode, q.price, c.delivered_cost
            FROM project_quote q JOIN project p ON p.id = q.project_id LEFT JOIN project_costing c ON c.quote_id = q.id
            WHERE p.public_id = :id AND p.owner_account_id = :owner
            """;

    private final JdbcClient jdbc;

    JdbcProjectQuoteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Optional<IssuedQuote> issue(Viewer viewer, ProjectId id, long expectedVersion, NewQuote quote) {
        record Locked(long key, int number, long version) {
        }
        var locked = jdbc.sql("""
                UPDATE project SET status = 'quoted', last_quote_number = last_quote_number + 1, version = version + 1
                WHERE public_id = :id AND owner_account_id = :owner AND version = :version AND status = 'draft'
                RETURNING id, last_quote_number, version
                """)
                .param("id", id.value())
                .param("owner", viewer.accountId().value())
                .param("version", expectedVersion)
                .query((rs, n) -> new Locked(rs.getLong("id"), rs.getInt("last_quote_number"), rs.getLong("version")))
                .optional();
        if (locked.isEmpty()) {
            return Optional.empty();
        }
        var project = locked.get();
        record Written(long key, Timestamp issuedAt) {
        }
        var written = jdbc.sql("""
                INSERT INTO project_quote (project_id, number, expires_at, buyer_name, note, mode, price, inputs_format_version, inputs)
                VALUES (:project, :number, :expires, :buyer, :note, :mode, :price, :format, CAST(:inputs AS jsonb))
                RETURNING id, issued_at
                """)
                .param("project", project.key())
                .param("number", project.number())
                .param("expires", quote.expiresAt() == null ? null : Timestamp.from(quote.expiresAt()), Types.TIMESTAMP)
                .param("buyer", quote.buyerName(), Types.VARCHAR)
                .param("note", quote.note(), Types.VARCHAR)
                .param("mode", quote.mode())
                .param("price", quote.price())
                .param("format", QuoteInputsV1.FORMAT)
                .param("inputs", DocumentJson.write(quote.inputs()))
                .query((rs, n) -> new Written(rs.getLong("id"), rs.getTimestamp("issued_at")))
                .single();
        // the Draft's costing is what the quote was priced from, so it moves into the quote. It must be for the version the caller saw.
        var costingId = jdbc.sql("""
                INSERT INTO project_costing (project_id, quote_id, costed_project_version, costed_sde_version, prices_as_of, costed_at,
                    delivered_cost, quotable, document_format_version, document)
                SELECT project_id, :quote, costed_project_version, costed_sde_version, prices_as_of, costed_at, delivered_cost,
                    quotable, document_format_version, document
                FROM project_costing WHERE project_id = :project AND quote_id IS NULL AND costed_project_version = :version AND quotable
                RETURNING id
                """)
                .param("quote", written.key())
                .param("project", project.key())
                .param("version", expectedVersion)
                .query(Long.class)
                .optional()
                .orElseThrow(CostingOutOfDateException::new);
        jdbc.sql("""
                INSERT INTO project_trace (costing_id, document_format_version, document)
                SELECT :copy, t.document_format_version, t.document
                FROM project_trace t JOIN project_costing c ON c.id = t.costing_id WHERE c.project_id = :project AND c.quote_id IS NULL
                """)
                .param("copy", costingId)
                .param("project", project.key())
                .update();
        jdbc.sql("DELETE FROM project_costing WHERE project_id = :project AND quote_id IS NULL")
                .param("project", project.key())
                .update();
        return Optional.of(new IssuedQuote(project.number(), project.version(), written.issuedAt().toInstant()));
    }

    @Override
    public List<QuoteSummary> list(Viewer viewer, ProjectId id) {
        return jdbc.sql(SUMMARY + " ORDER BY q.number")
                .param("id", id.value())
                .param("owner", viewer.accountId().value())
                .query((rs, n) -> summary(rs))
                .list();
    }

    @Override
    public Optional<QuoteSummary> find(Viewer viewer, ProjectId id, int number) {
        return jdbc.sql(SUMMARY + " AND q.number = :number")
                .param("id", id.value())
                .param("owner", viewer.accountId().value())
                .param("number", number)
                .query((rs, n) -> summary(rs))
                .optional();
    }

    private static QuoteSummary summary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new QuoteSummary(rs.getInt("number"), rs.getTimestamp("issued_at").toInstant(),
                rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(), rs.getString("buyer_name"),
                rs.getString("note"), rs.getString("mode"), rs.getBigDecimal("price"), rs.getBigDecimal("delivered_cost"));
    }

    @Override
    public Optional<QuoteInputsV1> findInputs(Viewer viewer, ProjectId id, int number) {
        return jdbc.sql("""
                SELECT q.inputs_format_version, q.inputs::text AS inputs
                FROM project_quote q JOIN project p ON p.id = q.project_id
                WHERE p.public_id = :id AND p.owner_account_id = :owner AND q.number = :number
                """)
                .param("id", id.value())
                .param("owner", viewer.accountId().value())
                .param("number", number)
                .query((rs, n) -> DocumentJson.readInputs(rs.getInt("inputs_format_version"), rs.getString("inputs")))
                .optional();
    }

    @Override
    public Optional<StoredCosting> findCosting(Viewer viewer, ProjectId id, int number) {
        return jdbc.sql("""
                SELECT c.costed_project_version, c.costed_sde_version, c.prices_as_of, c.costed_at, c.delivered_cost, c.quotable,
                    c.document_format_version, c.document::text AS document
                FROM project_costing c JOIN project_quote q ON q.id = c.quote_id JOIN project p ON p.id = q.project_id
                WHERE p.public_id = :id AND p.owner_account_id = :owner AND q.number = :number
                """)
                .param("id", id.value())
                .param("owner", viewer.accountId().value())
                .param("number", number)
                .query((rs, n) -> new StoredCosting(rs.getLong("costed_project_version"), rs.getString("costed_sde_version"),
                        rs.getTimestamp("prices_as_of") == null ? null : rs.getTimestamp("prices_as_of").toInstant(),
                        rs.getTimestamp("costed_at").toInstant(), rs.getBigDecimal("delivered_cost"), rs.getBoolean("quotable"),
                        DocumentJson.readCosting(rs.getInt("document_format_version"), rs.getString("document"))))
                .optional();
    }

    @Override
    public Optional<TraceDocumentV1> findTrace(Viewer viewer, ProjectId id, int number) {
        return jdbc.sql("""
                SELECT t.document_format_version, t.document::text AS document
                FROM project_trace t JOIN project_costing c ON c.id = t.costing_id JOIN project_quote q ON q.id = c.quote_id
                JOIN project p ON p.id = q.project_id
                WHERE p.public_id = :id AND p.owner_account_id = :owner AND q.number = :number
                """)
                .param("id", id.value())
                .param("owner", viewer.accountId().value())
                .param("number", number)
                .query((rs, n) -> DocumentJson.readTrace(rs.getInt("document_format_version"), rs.getString("document")))
                .optional();
    }

    @Override
    @Transactional
    public Optional<DeletedQuote> delete(Viewer viewer, ProjectId id, int number) {
        var projectKey = jdbc.sql("""
                DELETE FROM project_quote q USING project p
                WHERE q.project_id = p.id AND p.public_id = :id AND p.owner_account_id = :owner AND q.number = :number
                RETURNING q.project_id
                """)
                .param("id", id.value())
                .param("owner", viewer.accountId().value())
                .param("number", number)
                .query(Long.class)
                .optional();
        if (projectKey.isEmpty()) {
            return Optional.empty();
        }
        // the last quote of a Quoted project was what locked it, so with no quote left it is a Draft again
        var reopened = jdbc.sql("""
                UPDATE project SET status = 'draft', version = version + 1
                WHERE id = :project AND status = 'quoted' AND NOT EXISTS (SELECT 1 FROM project_quote WHERE project_id = :project)
                """)
                .param("project", projectKey.get())
                .update() > 0;
        return Optional.of(new DeletedQuote(reopened));
    }
}
