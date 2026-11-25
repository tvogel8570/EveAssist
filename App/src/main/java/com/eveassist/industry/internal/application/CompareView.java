package com.eveassist.industry.internal.application;

import com.eveassist.industry.ProjectId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One costing laid out the way the game's Industry window shows each of its jobs, so that the two can be read line against line (the comparison
 * views, layouts A and B of the mockup). It is made from the costing's stored trace. {@code source} says which costing it is (the Draft's latest, or
 * an issued quote's), {@code key} names it for what the browser keeps about it, and {@code outOfDate} says that a Draft was changed since.
 */
public record CompareView(
        ProjectId project, String projectName, Source source, int quoteNumber, Instant costedAt, boolean outOfDate, String key,
        List<Job> jobs) {

    public enum Source { DRAFT, QUOTE }

    public CompareView {
        jobs = List.copyOf(jobs);
    }

    /** One job as the window shows it. {@code unitsMade} is the output ("x 2350"). */
    public record Job(
            int index, String product, long runs, long unitsMade, int me, int te, String blueprintKind, List<Material> materials, Time time,
            Cost cost) {

        public Job {
            materials = List.copyOf(materials);
        }
    }

    /**
     * A material of the job. {@code quantity} is what the job uses, {@code shortened} is how the window shows it in the list ("0.31M") and
     * {@code steps} is how it came about from the base quantity.
     */
    public record Material(String name, long quantity, String shortened, String steps, boolean raisedToMinimum) {
    }

    /** The duration, to the nearest second, and the lines of its hover: each is the percent of the time that it saves. */
    public record Time(long seconds, BigDecimal exactSeconds, List<TimeLine> lines) {

        public Time {
            lines = List.copyOf(lines);
        }
    }

    /** A line of the duration hover. {@code kind} is blueprint, skills, structure or rig. {@code saved} is a percent to one decimal. */
    public record TimeLine(String kind, BigDecimal saved) {
    }

    /**
     * The cost hover, every line in whole ISK as the window rounds it. The window's total is the rounded gross cost plus the rounded taxes, so it
     * can differ from the exact total by a little.
     */
    public record Cost(
            long eiv, BigDecimal indexPercent, long indexLine, BigDecimal structureBonusPercent, long bonus, long gross, BigDecimal taxPercent,
            long facilityTax, BigDecimal sccPercent, long scc, long taxes, long total, BigDecimal exactTotal) {
    }
}
