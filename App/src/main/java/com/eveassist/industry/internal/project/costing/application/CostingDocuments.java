package com.eveassist.industry.internal.project.costing.application;

import com.eveassist.industry.internal.project.costing.domain.BoughtMaterial;
import com.eveassist.industry.internal.project.costing.domain.CostLine;
import com.eveassist.industry.internal.project.costing.domain.ItemDemand;
import com.eveassist.industry.internal.costing.domain.JobTerms;
import com.eveassist.industry.internal.costing.domain.MaterialUse;
import com.eveassist.industry.internal.project.domain.Project;
import com.eveassist.industry.internal.project.costing.domain.ProjectJobCost;
import com.eveassist.industry.internal.project.costing.domain.ProjectQuote;
import com.eveassist.industry.internal.project.domain.ProjectSettings.QuoteMode;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Turns a costing into its display documents. Pure functions: the documents are plain data that holds no domain object, which is what
 * lets a stored one outlive a change to the domain. The format of what is written is {@link CostingDocumentV1} and
 * {@link TraceDocumentV1}, and a change in what a field means is a new class beside them, not an edit of these.
 */
final class CostingDocuments {

    private CostingDocuments() {
    }

    static CostingDocumentV1 costing(ProjectCostResult result, QuoteMode mode, Instant costedAt) {
        var cost = result.cost();
        var age = result.adjustedPricesAge();
        var provenance = new CostingDocumentV1.Provenance(result.costedProjectVersion(), result.sdeVersion(), costedAt,
                result.pricesAsOf(), age == null ? null : age.confirmedAt(), age == null ? null : age.esiModifiedAt());
        var other = mode == QuoteMode.MARGIN ? QuoteMode.MARKET : QuoteMode.MARGIN;
        var comparison = result.comparison();
        var split = new CostingDocumentV1.Split(comparison.leastCost().deliveredCost(), comparison.deliveredCostDelta(),
                comparison.lineDeltas().stream().map(CostingDocuments::line).toList(), comparison.totalSecondsDelta(),
                comparison.longestJobSecondsSaved(), comparison.costPerHourSaved());
        return new CostingDocumentV1(provenance, cost.lines().stream().map(CostingDocuments::line).toList(),
                cost.deliveredCost(), cost.costPerUnit(), cost.totalSeconds(), cost.longestJobSeconds(), result.quotable(),
                quote(cost.quote(), mode), quote(cost.comparison(), other),
                cost.jobs().stream().map(CostingDocuments::job).toList(),
                cost.bought().stream().map(CostingDocuments::bought).toList(),
                result.warnings().stream().map(warning -> new CostingDocumentV1.Warning(name(warning.kind()), warning.type().value()))
                        .toList(),
                split);
    }

    static TraceDocumentV1 trace(
            ProjectCostResult result, Project project, Map<TypeId, BigDecimal> adjusted, String appCommit, Instant costedAt) {
        var config = project.config();
        var settings = config.settings();
        var inputs = new TraceDocumentV1.Inputs(name(config.priceSide()), settings.iskPerHour(), settings.iskPerCubicMeter(),
                settings.contractFee(), name(settings.mode()), settings.marginPercent(), settings.premiumPercent(),
                settings.quoteStep(), name(config.defaultPolicy().strategy()), config.defaultPolicy().maxJobSeconds(),
                config.builder().industry(), config.builder().advancedIndustry(), config.builder().implantTimePercent());
        var cost = result.cost();
        // the project's own prices for what it buys and for what it makes, which is what the costing and a market quote used
        var priced = new TreeSet<Integer>(Comparator.naturalOrder());
        cost.bought().forEach(item -> priced.add(item.type().value()));
        project.products().forEach(product -> priced.add(product.type().value()));
        var unitPrices = new ArrayList<TraceDocumentV1.UnitPrice>();
        for (var type : priced) {
            var price = project.prices().get(new TypeId(type));
            if (price != null) {
                unitPrices.add(new TraceDocumentV1.UnitPrice(type, price.sell(), price.buy(), price.asOf()));
            }
        }
        return new TraceDocumentV1(
                new TraceDocumentV1.Data(appCommit, result.sdeVersion(), costedAt, result.costedProjectVersion()), inputs,
                cost.demands().stream().map(CostingDocuments::demand).toList(),
                cost.jobs().stream().map(job -> traceJob(job, adjusted)).toList(), unitPrices);
    }

    private static CostingDocumentV1.Line line(CostLine line) {
        return new CostingDocumentV1.Line(name(line.component()), line.amount());
    }

    private static CostingDocumentV1.Quote quote(ProjectQuote quote, QuoteMode mode) {
        return quote == null ? null : new CostingDocumentV1.Quote(name(mode), quote.price(), quote.pricePerUnit(), quote.profit(),
                quote.realizedMarginPercent());
    }

    private static CostingDocumentV1.Job job(ProjectJobCost planned) {
        var job = planned.job();
        return new CostingDocumentV1.Job(job.product().value(), job.runs(), job.unitsMade(), job.seconds(),
                job.estimatedItemValue(), job.installCost(), planned.blueprintCost(), name(planned.kind()),
                job.materials().stream().map(material -> new CostingDocumentV1.Material(material.material().value(),
                        material.quantity())).toList());
    }

    private static CostingDocumentV1.Bought bought(BoughtMaterial item) {
        return new CostingDocumentV1.Bought(item.type().value(), item.quantity(), item.unitPrice(), item.cost());
    }

    private static TraceDocumentV1.Demand demand(ItemDemand demand) {
        return new TraceDocumentV1.Demand(demand.type().value(), demand.unitsNeeded(), demand.runsNeeded(), demand.jobs(),
                demand.unitsMade());
    }

    private static TraceDocumentV1.Job traceJob(ProjectJobCost planned, Map<TypeId, BigDecimal> adjusted) {
        var job = planned.job();
        var terms = planned.terms();
        var time = job.time();
        var install = job.installBreakdown();
        return new TraceDocumentV1.Job(job.product().value(), job.runs(), (int) (job.unitsMade() / job.runs()),
                name(planned.kind()), terms.me(), terms.te(), facility(terms), builder(terms),
                job.materialUses().stream().map(CostingDocuments::material).toList(),
                new TraceDocumentV1.Time(time.baseSecondsPerRun(), time.blueprintFactor(), time.skillsAndImplantsFactor(),
                        time.structureFactor(), time.rigFactor(), time.exactSeconds(), time.seconds(), time.maximumRuns()),
                new TraceDocumentV1.Install(install.estimatedItemValue(), install.systemCostIndexPercent(), install.jobFee(),
                        install.structureBonus(), install.grossCost(), install.facilityTax(), install.sccSurcharge(), install.total()),
                job.materialUses().stream().map(use -> new TraceDocumentV1.EivLine(use.material().value(), use.baseQuantity(),
                        adjusted.get(use.material()))).toList());
    }

    private static TraceDocumentV1.Material material(MaterialUse use) {
        return new TraceDocumentV1.Material(use.material().value(), use.baseQuantity(), use.exactProduct(),
                use.roundedToTwoDecimals(), use.quantity(), use.raisedToMinimum());
    }

    private static TraceDocumentV1.Facility facility(JobTerms terms) {
        var facility = terms.facility();
        return new TraceDocumentV1.Facility(facility.structureMaterialPercent(), facility.rigMaterialPercent(),
                facility.structureTimePercent(), facility.rigTimePercent(), facility.structureCostPercent(),
                facility.systemCostIndexPercent(), facility.taxPercent(), facility.sccSurchargePercent());
    }

    private static TraceDocumentV1.Builder builder(JobTerms terms) {
        var builder = terms.builder();
        return new TraceDocumentV1.Builder(builder.industry(), builder.advancedIndustry(), builder.implantTimePercent(),
                builder.productSkills().stream().map(skill -> new TraceDocumentV1.Skill(skill.level(), skill.percentPerLevel()))
                        .toList());
    }

    /** Names are stored as the lower-case words the tables use for their enumerations, so a document reads without the code. */
    private static String name(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
