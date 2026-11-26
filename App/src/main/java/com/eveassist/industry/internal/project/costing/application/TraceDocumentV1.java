package com.eveassist.industry.internal.project.costing.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * What a costing was made from and how each number came about, in the shape of the game's windows, so that a difference with the game can
 * be traced to one line (format version 1). It is a display document, kept apart from the costing because it is large, and it is never
 * turned back into domain objects. See {@link CostingDocumentV1} for how format versions work.
 */
public record TraceDocumentV1(
        Data data, Inputs inputs, List<Demand> demands, List<Job> jobs, List<UnitPrice> unitPrices) {

    public static final int FORMAT = 1;

    public TraceDocumentV1 {
        demands = List.copyOf(demands);
        jobs = List.copyOf(jobs);
        unitPrices = List.copyOf(unitPrices);
    }

    /** The build and the data it ran on. {@code appCommit} is the short commit id the running image was built from, or dev. */
    public record Data(String appCommit, String sdeVersion, Instant costedAt, long costedProjectVersion) {
    }

    /** The project's rates and defaults, and the builder's levels that apply to every job. */
    public record Inputs(
            String priceSide, BigDecimal iskPerHour, BigDecimal iskPerCubicMeter, BigDecimal contractFee, String quoteMode,
            BigDecimal marginPercent, BigDecimal premiumPercent, BigDecimal quoteStep, String defaultSplitStrategy,
            Long defaultMaxJobSeconds, int industryLevel, int advancedIndustryLevel, BigDecimal implantTimePercent) {
    }

    /** How the demand for a built item came about and how many jobs its runs were shared into. */
    public record Demand(int type, long unitsNeeded, long runsNeeded, int jobs, long unitsMade) {
    }

    /** One job: what it was costed under, and every number the game's job window shows for it. */
    public record Job(
            int product, long runs, int unitsPerRun, String blueprintKind, int me, int te, Facility facility, Builder builder,
            List<Material> materials, Time time, Install install, List<EivLine> eivLines) {

        public Job {
            materials = List.copyOf(materials);
            eivLines = List.copyOf(eivLines);
        }
    }

    public record Facility(
            BigDecimal structureMaterialPercent, BigDecimal rigMaterialPercent, BigDecimal structureTimePercent,
            BigDecimal rigTimePercent, BigDecimal structureCostPercent, BigDecimal systemCostIndexPercent, BigDecimal taxPercent,
            BigDecimal sccSurchargePercent) {
    }

    public record Builder(int industry, int advancedIndustry, BigDecimal implantTimePercent, List<Skill> productSkills) {

        public Builder {
            productSkills = List.copyOf(productSkills);
        }
    }

    /** A time-saving skill the blueprint requires: the builder's level and the percent each level saves. */
    public record Skill(int level, BigDecimal percentPerLevel) {
    }

    /**
     * One material of the job: the amount for one run at ME 0, the exact product of that with the runs and the reductions, the product to
     * two decimals (half up), and what the job uses. {@code raisedToMinimum} is true where the one-per-run minimum set the quantity.
     */
    public record Material(
            int type, long baseQuantity, BigDecimal exactProduct, BigDecimal roundedToTwoDecimals, long quantity,
            boolean raisedToMinimum) {
    }

    /** The duration hover: each factor is the share of the time that is left, and they multiply. */
    public record Time(
            long baseSecondsPerRun, BigDecimal blueprintFactor, BigDecimal skillsAndImplantsFactor, BigDecimal structureFactor,
            BigDecimal rigFactor, BigDecimal exactSeconds, long seconds, long maximumRuns) {
    }

    /** The cost hover: the lines of the job's install cost. */
    public record Install(
            BigDecimal estimatedItemValue, BigDecimal systemCostIndexPercent, BigDecimal jobFee, BigDecimal structureBonus,
            BigDecimal grossCost, BigDecimal facilityTax, BigDecimal sccSurcharge, BigDecimal total) {
    }

    /** A base material of the job at its adjusted price, which the estimated item value adds up. The price is null if there is none. */
    public record EivLine(int type, long baseQuantity, BigDecimal adjustedPrice) {
    }

    /** The project's own price of an item it buys or makes, with the time it was taken. */
    public record UnitPrice(int type, BigDecimal sell, BigDecimal buy, Instant asOf) {
    }
}
