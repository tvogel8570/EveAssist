package com.eveassist.industry.internal.project.quote.application;

/** @param projectReopened true if this was the only quote of a Quoted project, which is now a Draft again */
public record QuoteDeleted(boolean projectReopened) {
}
