package com.eveassist.industry.internal.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.ProjectId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** The quotes of a project. Every method filters on the viewer in its SQL, so another account's project looks the same as a missing one. */
@SecondaryPort
public interface ProjectQuoteRepository {

    /** What a quote is issued with: who it is for, how it was priced, and the inputs it was costed from. */
    record NewQuote(String buyerName, String note, Instant expiresAt, String mode, BigDecimal price, QuoteInputsV1 inputs) {
    }

    /** The quote's number, and the version the project has now that it is quoted. */
    record IssuedQuote(int number, long projectVersion, Instant issuedAt) {
    }

    /** A quote as a list shows it. {@code deliveredCost} is to the cent, and the exact amount is in the quote's costing. */
    record QuoteSummary(
            int number, Instant issuedAt, Instant expiresAt, String buyerName, String note, String mode, BigDecimal price,
            BigDecimal deliveredCost) {
    }

    record DeletedQuote(boolean projectReopened) {
    }

    /**
     * Issues the next quote in one transaction: the project becomes Quoted (its version rises and {@code last_quote_number} counts the
     * quote), the quote is written, and the Draft's costing and trace **move** into it. Only a costing for exactly the version the caller
     * saw, and that can be quoted, is moved; otherwise {@link CostingOutOfDateException} is thrown and nothing changes. Empty if the project
     * is missing, is not a Draft, or is not at {@code expectedVersion}.
     */
    Optional<IssuedQuote> issue(Viewer viewer, ProjectId id, long expectedVersion, NewQuote quote);

    List<QuoteSummary> list(Viewer viewer, ProjectId id);

    Optional<QuoteSummary> find(Viewer viewer, ProjectId id, int number);

    Optional<QuoteInputsV1> findInputs(Viewer viewer, ProjectId id, int number);

    Optional<StoredCosting> findCosting(Viewer viewer, ProjectId id, int number);

    Optional<TraceDocumentV1> findTrace(Viewer viewer, ProjectId id, int number);

    /**
     * Deletes the quote with its costing and trace. If the project is Quoted and this was its only quote, it becomes a Draft again
     * (its version rises), since no quote stands any more. Quote numbers are not reused. Empty if there is no such quote.
     */
    Optional<DeletedQuote> delete(Viewer viewer, ProjectId id, int number);
}
