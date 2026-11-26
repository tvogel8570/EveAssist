package com.eveassist.industry.internal.project.quote.application;

import com.eveassist.industry.internal.project.costing.application.CostingDocumentV1;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Everything a quote was costed from, kept with the quote (format version 1), so that a new version can be made from it later. Unlike the
 * display documents, this one is read back into domain objects, which is why it has a mapper in each direction ({@code QuoteInputs}) and a
 * format of its own. Facilities are kept by their id **and by their values**, because a facility can change or be deleted after the quote
 * was issued, and the quote must stay what it was. The format version is a column. See {@link CostingDocumentV1} for how format versions work.
 */
public record QuoteInputsV1(
        List<Product> products, List<Integer> boughtParts, List<Item> items, Config config, List<Price> prices,
        List<FacilityValues> facilities) {

    public static final int FORMAT = 1;

    public QuoteInputsV1 {
        products = List.copyOf(products);
        boughtParts = List.copyOf(boughtParts);
        items = List.copyOf(items);
        prices = List.copyOf(prices);
        facilities = List.copyOf(facilities);
    }

    public record Product(int type, long quantity) {
    }

    /** How one built item was made. {@code facility} is a facility's public id as text, or null for the project's default. */
    /** {@code skills} of an item are only in quotes stored before 2026-11-25, when the levels moved to the {@link Builder}. They are written empty now. */
    public record Item(
            int type, String blueprintSource, int me, int te, BigDecimal blueprintCostPerRun, String facility, Policy policy,
            List<Skill> skills) {

        public Item {
            skills = List.copyOf(skills);
        }
    }

    /** How runs are split: a strategy, and the longest a job may take, or null for no bound. */
    public record Policy(String strategy, Long maxJobSeconds) {
    }

    public record Skill(int skill, int level) {
    }

    public record Config(
            String priceSide, BigDecimal iskPerHour, BigDecimal iskPerCubicMeter, BigDecimal contractFee, String quoteMode,
            BigDecimal marginPercent, BigDecimal premiumPercent, BigDecimal quoteStep, Policy defaultPolicy, String defaultFacility,
            Builder builder, String buyerName, String note) {
    }

    /**
     * The builder the quote was costed with. {@code skills} are the levels in the skills only some blueprints require. A quote stored before 2026-11-25
     * has none here: it kept them on each {@link Item}.
     */
    public record Builder(int industry, int advancedIndustry, BigDecimal implantTimePercent, List<Skill> skills) {

        public Builder {
            skills = skills == null ? List.of() : List.copyOf(skills);
        }
    }

    /**
     * The price of an item the quote was costed with. {@code typed} says that the user typed it (so it is one number on both sides, and a new version made from
     * the quote keeps it typed, which a refresh of the market prices then leaves). A document stored before typed prices existed has no such field, so it is read
     * as nothing and made false: it must be a {@code Boolean} for that, since a missing primitive makes the whole document unreadable.
     */
    public record Price(int type, BigDecimal sell, BigDecimal buy, Instant asOf, Boolean typed) {

        public Price {
            typed = typed != null && typed;
        }

        /** A price taken from the market, which is every price of a quote issued before typed prices existed. */
        public Price(int type, BigDecimal sell, BigDecimal buy, Instant asOf) {
            this(type, sell, buy, asOf, false);
        }
    }

    /** What a facility did to a job when the quote was costed. */
    public record FacilityValues(
            String id, BigDecimal structureMaterialPercent, BigDecimal rigMaterialPercent, BigDecimal structureTimePercent,
            BigDecimal rigTimePercent, BigDecimal structureCostPercent, BigDecimal systemCostIndexPercent, BigDecimal taxPercent,
            BigDecimal sccSurchargePercent) {
    }
}
