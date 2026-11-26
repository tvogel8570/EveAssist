package com.eveassist.industry.internal.project.costing.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A costing as a page shows it, stored as it was at the time (format version 1). It is a display document: it is read by the page that
 * shows it and is never turned back into domain objects, so a later change to the domain does not break a stored one. Items are type ids,
 * and names come from the SDE when shown. The format version is a column of its table and not part of this record, and a change that
 * alters what a field means is a new class beside this one, with this one kept for as long as a document of this format is stored.
 * A new optional field is not a new format: a document stored before it simply lacks it.
 */
public record CostingDocumentV1(
        Provenance provenance, List<Line> lines, BigDecimal deliveredCost, BigDecimal costPerUnit, long totalSeconds,
        long longestJobSeconds, boolean quotable, Quote quote, Quote comparison, List<Job> jobs, List<Bought> bought,
        List<Warning> warnings, Split split) {

    /** The format version this class is, as the {@code document_format_version} column holds it. */
    public static final int FORMAT = 1;

    public CostingDocumentV1 {
        lines = List.copyOf(lines);
        jobs = List.copyOf(jobs);
        bought = List.copyOf(bought);
        warnings = List.copyOf(warnings);
    }

    /**
     * What the costing was made from and when. {@code adjustedPricesConfirmedAt} and {@code adjustedPricesEsiModifiedAt} are the two ages
     * of the adjusted prices (see {@code AdjustedPriceAge}), either of which is null if unknown.
     */
    public record Provenance(
            long costedProjectVersion, String sdeVersion, Instant costedAt, Instant pricesAsOf,
            Instant adjustedPricesConfirmedAt, Instant adjustedPricesEsiModifiedAt) {
    }

    /** A step of the delivered cost: the component's name and its amount (the surplus credit is negative). */
    public record Line(String component, BigDecimal amount) {
    }

    public record Quote(String mode, BigDecimal price, BigDecimal pricePerUnit, BigDecimal profit, BigDecimal realizedMarginPercent) {
    }

    public record Job(
            int product, long runs, long unitsMade, long seconds, BigDecimal estimatedItemValue, BigDecimal installCost,
            BigDecimal blueprintCost, String blueprintKind, List<Material> materials) {

        public Job {
            materials = List.copyOf(materials);
        }
    }

    public record Material(int type, long quantity) {
    }

    public record Bought(int type, long quantity, BigDecimal unitPrice, BigDecimal cost) {
    }

    public record Warning(String kind, int type) {
    }

    /** What the project's split policies cost against least cost, and what they buy in time. */
    public record Split(
            BigDecimal leastCostDeliveredCost, BigDecimal deliveredCostDelta, List<Line> lineDeltas, long totalSecondsDelta,
            long longestJobSecondsSaved, BigDecimal costPerHourSaved) {

        public Split {
            lineDeltas = List.copyOf(lineDeltas);
        }
    }
}
