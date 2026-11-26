package com.eveassist.industry.internal.project.quote.application;

import com.eveassist.industry.ProjectId;
import java.time.Instant;

/** A quote that was issued: its number, and the version the project has now that it is quoted and locked. */
public record QuoteIssued(ProjectId project, int number, long projectVersion, Instant issuedAt) {
}
