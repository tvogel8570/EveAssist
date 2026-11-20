package com.eveassist.industry.internal.application;

/** A quote with its costing, which is what a page shows for it. The costing is frozen: the document is as it was when the quote was issued. */
public record QuoteView(ProjectQuoteRepository.QuoteSummary quote, StoredCosting costing) {
}
