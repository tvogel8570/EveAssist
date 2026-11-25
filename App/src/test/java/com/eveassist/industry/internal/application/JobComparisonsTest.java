package com.eveassist.industry.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * What the game's window shows for a stored job. The numbers are reading G11 (costing-detail.md): 235 runs of the ME 10 / TE 20 Cap Booster 800 copy
 * in the wormhole Azbel, where the window showed Nocxium 2,723, an estimated item value of 10,280,639, an index line of 58,430, a role bonus of -2,337,
 * a gross cost of 56,093, an SCC surcharge of 411,226 and a total of 467,319, which is the rounded gross cost plus the rounded taxes (the exact total
 * is 467,318.39).
 */
class JobComparisonsTest {

    private static final Map<Integer, String> NAMES = Map.of(1, "Cap Booster 800", 34, "Tritanium", 38, "Nocxium");

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static TraceDocumentV1.Job job(String blueprintFactor, String skillsFactor, String structureFactor, String rigFactor) {
        var materials = List.of(
                new TraceDocumentV1.Material(34, 1502, d("314496.27"), d("314496.27"), 314497, false),
                new TraceDocumentV1.Material(38, 13, d("2722.005"), d("2722.01"), 2723, false));
        var time = new TraceDocumentV1.Time(900, d(blueprintFactor), d(skillsFactor), d(structureFactor), d(rigFactor), d("92044.8"), 92045, 1500);
        var install = new TraceDocumentV1.Install(d("10280639.49"), d("0.56835"), d("58430.014541415"), d("-2337.2005816566"),
                d("56092.8139597584"), BigDecimal.ZERO, d("411225.5796"), d("467318.3935597584"));
        var facility = new TraceDocumentV1.Facility(d("1"), d("0"), d("20"), d("0"), d("4"), d("0.56835"), d("0"), d("4"));
        var builder = new TraceDocumentV1.Builder(5, 5, BigDecimal.ZERO, List.of());
        return new TraceDocumentV1.Job(1, 235, 10, "copy", 10, 20, facility, builder, materials, time, install, List.of());
    }

    private static CompareView.Job g11() {
        return JobComparisons.of(0, job("0.8", "0.68", "0.8", "1"), 2350, NAMES::get);
    }

    @Test
    void theMaterialsAreNamedWithWhatTheListAndTheHoverShow() {
        var materials = g11().materials();

        assertThat(materials).extracting(CompareView.Material::name).containsExactly("Tritanium", "Nocxium");
        assertThat(materials).extracting(CompareView.Material::quantity).containsExactly(314497L, 2723L);
        assertThat(materials).extracting(CompareView.Material::shortened).containsExactly("0.31M", "2,723");
        // the exact product, then the two decimals when they differ, then the quantity, which rounds the half up
        assertThat(materials.get(0).steps()).isEqualTo("1,502 × 235 runs × 0.891 = 314496.27 → 314,497");
        assertThat(materials.get(1).steps()).isEqualTo("13 × 235 runs × 0.891 = 2722.005 → 2722.01 → 2,723");
    }

    @Test
    void theDurationHasTheLinesOfItsHoverAndLeavesOutOneThatSavesNothing() {
        var time = g11().time();

        assertThat(time.seconds()).isEqualTo(92045);
        assertThat(time.lines()).extracting(CompareView.TimeLine::kind).containsExactly("blueprint", "skills", "structure");
        assertThat(time.lines()).extracting(CompareView.TimeLine::saved).containsExactly(d("20.0"), d("32.0"), d("20.0"));

        // a blueprint of TE 0 has no line, and a rig adds one
        var plain = JobComparisons.of(0, job("1", "0.6528", "0.8", "0.9"), 2350, NAMES::get).time();
        assertThat(plain.lines()).extracting(CompareView.TimeLine::kind).containsExactly("skills", "structure", "rig");
        // 0.6528 saves 34.72%, which the window shows as 34.7 (reading G9)
        assertThat(plain.lines().getFirst().saved()).isEqualTo(d("34.7"));
        assertThat(plain.lines().getLast().saved()).isEqualTo(d("10.0"));
    }

    @Test
    void theCostIsRoundedTheWayTheWindowDoesAndItsTotalIsTheRoundedGrossPlusTheRoundedTaxes() {
        var cost = g11().cost();

        assertThat(cost.eiv()).isEqualTo(10280639L);
        assertThat(cost.indexPercent()).isEqualByComparingTo("0.56835");
        assertThat(cost.indexLine()).isEqualTo(58430L);
        assertThat(cost.bonus()).isEqualTo(-2337L);
        assertThat(cost.gross()).isEqualTo(56093L);
        assertThat(cost.scc()).isEqualTo(411226L);
        assertThat(cost.facilityTax()).isZero();
        assertThat(cost.taxes()).isEqualTo(411226L);
        assertThat(cost.total()).isEqualTo(467319L);
        // the exact total is a little less, which is what the page tells the user
        assertThat(cost.exactTotal()).isEqualByComparingTo("467318.3935597584");
    }

    @Test
    void aQuantityIsShortenedAsTheWindowsListDoes() {
        assertThat(JobComparisons.shortened(1_257)).isEqualTo("1,257");
        assertThat(JobComparisons.shortened(9_999)).isEqualTo("9,999");
        assertThat(JobComparisons.shortened(10_000)).isEqualTo("10.00K");
        assertThat(JobComparisons.shortened(92_130)).isEqualTo("92.13K");
        assertThat(JobComparisons.shortened(99_999)).isEqualTo("100.00K");
        assertThat(JobComparisons.shortened(100_000)).isEqualTo("0.10M");
        assertThat(JobComparisons.shortened(314_497)).isEqualTo("0.31M");
        assertThat(JobComparisons.shortened(1_250_000)).isEqualTo("1.25M");
    }

    @Test
    void aMaterialRaisedToTheMinimumSaysSo() {
        var job = new TraceDocumentV1.Job(1, 1000, 1, "copy", 10, 0,
                new TraceDocumentV1.Facility(d("0"), d("0"), d("0"), d("0"), d("0"), d("1"), d("0"), d("4")),
                new TraceDocumentV1.Builder(5, 5, BigDecimal.ZERO, List.of()),
                List.of(new TraceDocumentV1.Material(38, 1, d("891"), d("891"), 1000, true)),
                new TraceDocumentV1.Time(900, d("1"), d("1"), d("1"), d("1"), d("900"), 900, 1),
                new TraceDocumentV1.Install(d("100"), d("1"), d("1"), d("0"), d("1"), d("0"), d("4"), d("5")), List.of());

        var material = JobComparisons.of(0, job, 1000, NAMES::get).materials().getFirst();

        assertThat(material.raisedToMinimum()).isTrue();
        assertThat(material.steps()).endsWith("(one for each run, at least)");
    }
}
