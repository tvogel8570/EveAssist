package com.eveassist.industry.internal.project.costing.application;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A costing as it was stored. The provenance is in columns, so a list can say whether a costing is out of date without reading the
 * document, and the document is the costing a page shows.
 *
 * @param costedProjectVersion the project's edit version it was costed for, which says when it is out of date
 * @param sdeVersion the SDE import it used
 * @param pricesAsOf the age of the oldest price among what is bought, or null
 * @param deliveredCost the delivered cost **to the cent**, for lists. The exact amount is in the document
 */
public record StoredCosting(
        long costedProjectVersion, String sdeVersion, Instant pricesAsOf, Instant costedAt, BigDecimal deliveredCost,
        boolean quotable, CostingDocumentV1 document) {
}
