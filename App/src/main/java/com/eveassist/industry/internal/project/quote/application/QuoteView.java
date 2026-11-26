package com.eveassist.industry.internal.project.quote.application;

import com.eveassist.industry.internal.project.costing.application.StoredCosting;

/** A quote with its costing, which is what a page shows for it. The costing is frozen: the document is as it was when the quote was issued. */
public record QuoteView(ProjectQuoteRepository.QuoteSummary quote, StoredCosting costing) {
}
