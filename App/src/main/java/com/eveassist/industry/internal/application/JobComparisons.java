package com.eveassist.industry.internal.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;

/**
 * Turns a job of a stored trace into what the game's Industry window shows for it: each material as the list shows it and as the hover shows it, the
 * duration with the lines of its hover, and the cost hover rounded to whole ISK the way the window does it. Nothing is worked out again: every number is one
 * the costing stored, and only the rounding is the window's.
 */
final class JobComparisons {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private JobComparisons() {
    }

    static CompareView.Job of(int index, TraceDocumentV1.Job job, long unitsMade, IntFunction<String> names) {
        var materials = job.materials().stream().map(material -> material(material, job.runs(), names)).toList();
        return new CompareView.Job(index, names.apply(job.product()), job.runs(), unitsMade, job.me(), job.te(), job.blueprintKind(),
                materials, time(job.time()), cost(job.install(), job.facility()));
    }

    private static CompareView.Material material(TraceDocumentV1.Material material, long runs, IntFunction<String> names) {
        return new CompareView.Material(names.apply(material.type()), material.quantity(), shortened(material.quantity()),
                steps(material, runs), material.raisedToMinimum());
    }

    /**
     * How the window shortens a quantity in its list: 0.31M from 100,000 on, 92.13K from 10,000, and the number itself below. It keeps two decimals,
     * which is why a hover has the exact quantity.
     */
    static String shortened(long quantity) {
        if (quantity >= 100_000) {
            return BigDecimal.valueOf(quantity).divide(BigDecimal.valueOf(1_000_000), 2, RoundingMode.HALF_UP).toPlainString() + "M";
        }
        if (quantity >= 10_000) {
            return BigDecimal.valueOf(quantity).divide(BigDecimal.valueOf(1_000), 2, RoundingMode.HALF_UP).toPlainString() + "K";
        }
        return grouped(quantity);
    }

    /** The base quantity of a run, the runs and the share that is left, and what they make, with the rounding the window does (two decimals, then up). */
    private static String steps(TraceDocumentV1.Material material, long runs) {
        var base = BigDecimal.valueOf(material.baseQuantity()).multiply(BigDecimal.valueOf(runs));
        var factor = base.signum() == 0 ? BigDecimal.ONE : material.exactProduct().divide(base, 4, RoundingMode.HALF_UP);
        var text = new StringBuilder(grouped(material.baseQuantity())).append(" × ").append(runs).append(runs == 1 ? " run" : " runs")
                .append(" × ").append(factor.stripTrailingZeros().toPlainString()).append(" = ").append(plain(material.exactProduct()));
        if (material.exactProduct().compareTo(material.roundedToTwoDecimals()) != 0) {
            text.append(" → ").append(plain(material.roundedToTwoDecimals()));
        }
        text.append(" → ").append(grouped(material.quantity()));
        if (material.raisedToMinimum()) {
            text.append(" (one for each run, at least)");
        }
        return text.toString();
    }

    private static CompareView.Time time(TraceDocumentV1.Time time) {
        var lines = new ArrayList<CompareView.TimeLine>();
        addLine(lines, "blueprint", time.blueprintFactor());
        addLine(lines, "skills", time.skillsAndImplantsFactor());
        addLine(lines, "structure", time.structureFactor());
        addLine(lines, "rig", time.rigFactor());
        return new CompareView.Time(time.seconds(), time.exactSeconds(), lines);
    }

    /** The window leaves out a line that saves nothing (a blueprint of TE 0), so a factor of 1 gives none. */
    private static void addLine(List<CompareView.TimeLine> lines, String kind, BigDecimal factor) {
        var saved = BigDecimal.ONE.subtract(factor).multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP);
        if (saved.signum() != 0) {
            lines.add(new CompareView.TimeLine(kind, saved));
        }
    }

    private static CompareView.Cost cost(TraceDocumentV1.Install install, TraceDocumentV1.Facility facility) {
        var taxes = whole(install.facilityTax().add(install.sccSurcharge()));
        var gross = whole(install.grossCost());
        return new CompareView.Cost(whole(install.estimatedItemValue()), install.systemCostIndexPercent(), whole(install.jobFee()),
                facility.structureCostPercent(), whole(install.structureBonus()), gross, facility.taxPercent(), whole(install.facilityTax()),
                facility.sccSurchargePercent(), whole(install.sccSurcharge()), taxes, gross + taxes, install.total());
    }

    private static long whole(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static String grouped(long number) {
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(number);
    }

    /** A decimal without trailing zeros and without an exponent, such as 2722.005 and 314496.27. */
    private static String plain(BigDecimal number) {
        return number.stripTrailingZeros().toPlainString();
    }
}
