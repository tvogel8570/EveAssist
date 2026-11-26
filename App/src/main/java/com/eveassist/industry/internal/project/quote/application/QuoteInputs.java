package com.eveassist.industry.internal.project.quote.application;

import com.eveassist.industry.FacilityId;
import com.eveassist.industry.internal.costing.domain.FacilityTerms;
import com.eveassist.industry.internal.costing.domain.PlanPrice;
import com.eveassist.industry.internal.costing.domain.PlanProduct;
import com.eveassist.industry.internal.costing.domain.SplitPolicy;
import com.eveassist.industry.internal.costing.domain.SplitStrategy;
import com.eveassist.industry.internal.project.application.UnreadableDocumentException;
import com.eveassist.industry.internal.project.domain.BlueprintSource;
import com.eveassist.industry.internal.project.domain.PriceSide;
import com.eveassist.industry.internal.project.domain.Project;
import com.eveassist.industry.internal.project.domain.ProjectBuilder;
import com.eveassist.industry.internal.project.domain.ProjectConfig;
import com.eveassist.industry.internal.project.domain.ProjectItem;
import com.eveassist.industry.internal.project.domain.ProjectSettings;
import com.eveassist.industry.internal.project.domain.ProjectSettings.QuoteMode;
import com.eveassist.sde.TypeId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The inputs snapshot of a quote, in both directions: a Draft into {@link QuoteInputsV1} when a quote is issued, and a stored one back into
 * what a Draft is made of when a new version is made from it. Reading goes through the domain's own constructors, so the values are
 * checked on the way in, and a document that no longer satisfies them is reported as unreadable and not half-applied.
 */
public final class QuoteInputs {

    private QuoteInputs() {
    }

    /** What a Draft's inputs become when restored from a snapshot, and which facilities could not be restored as they were. */
    record Restored(
            List<PlanProduct> products, Set<TypeId> boughtParts, Map<TypeId, ProjectItem> items, Map<TypeId, PlanPrice> prices,
            Set<TypeId> typedPrices, ProjectConfig config, List<FacilityId> changedFacilities, List<FacilityId> goneFacilities) {
    }

    /** Every facility the project refers to, by its default and by its items. */
    public static Set<FacilityId> facilitiesOf(Project project) {
        Set<FacilityId> ids = new HashSet<>();
        if (project.config().defaultFacility() != null) {
            ids.add(project.config().defaultFacility());
        }
        project.items().values().stream().filter(item -> item.facility() != null).forEach(item -> ids.add(item.facility()));
        return ids;
    }

    static QuoteInputsV1 snapshot(Project project, Map<FacilityId, FacilityTerms> facilityValues) {
        var config = project.config();
        var settings = config.settings();
        var items = new ArrayList<QuoteInputsV1.Item>();
        new TreeSet<>(project.items().keySet().stream().map(TypeId::value).toList()).forEach(type -> {
            var item = project.items().get(new TypeId(type));
            items.add(new QuoteInputsV1.Item(type, name(item.source()), item.me(), item.te(), item.blueprintCostPerRun(),
                    item.facility() == null ? null : item.facility().value().toString(), policy(item.policy()), List.of()));
        });
        var prices = new ArrayList<QuoteInputsV1.Price>();
        new TreeSet<>(project.prices().keySet().stream().map(TypeId::value).toList()).forEach(type -> {
            var price = project.prices().get(new TypeId(type));
            prices.add(new QuoteInputsV1.Price(type, price.sell(), price.buy(), price.asOf(), project.typedPrices().contains(new TypeId(type))));
        });
        var facilities = new ArrayList<QuoteInputsV1.FacilityValues>();
        facilityValues.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey(
                java.util.Comparator.comparing(id -> id.value().toString()))).forEach(entry -> {
            var terms = entry.getValue();
            facilities.add(new QuoteInputsV1.FacilityValues(entry.getKey().value().toString(), terms.structureMaterialPercent(),
                    terms.rigMaterialPercent(), terms.structureTimePercent(), terms.rigTimePercent(), terms.structureCostPercent(),
                    terms.systemCostIndexPercent(), terms.taxPercent(), terms.sccSurchargePercent()));
        });
        return new QuoteInputsV1(
                project.products().stream().map(product -> new QuoteInputsV1.Product(product.type().value(), product.quantity())).toList(),
                new TreeSet<>(project.boughtParts().stream().map(TypeId::value).toList()).stream().toList(), items,
                new QuoteInputsV1.Config(name(config.priceSide()), settings.iskPerHour(), settings.iskPerCubicMeter(),
                        settings.contractFee(), name(settings.mode()), settings.marginPercent(), settings.premiumPercent(),
                        settings.quoteStep(), policy(config.defaultPolicy()),
                        config.defaultFacility() == null ? null : config.defaultFacility().value().toString(),
                        new QuoteInputsV1.Builder(config.builder().industry(), config.builder().advancedIndustry(),
                                config.builder().implantTimePercent(), skills(config.builder())),
                        config.buyerName(), config.note()),
                prices, facilities);
    }

    /**
     * Turns a snapshot back into inputs. A facility that still exists is referred to by its id, as before; one that no longer exists is
     * left out (the item falls back to the project's default, which may be none), and both it and a facility whose values are not what
     * the quote was costed with are reported, so the developer is told before costing again.
     */
    static Restored restore(QuoteInputsV1 inputs, Map<FacilityId, FacilityTerms> live) {
        try {
            var snapshotValues = new HashMap<FacilityId, FacilityTerms>();
            inputs.facilities().forEach(values -> snapshotValues.put(new FacilityId(UUID.fromString(values.id())), terms(values)));
            var changed = new ArrayList<FacilityId>();
            var gone = new ArrayList<FacilityId>();
            snapshotValues.forEach((id, quoted) -> {
                var now = live.get(id);
                if (now == null) {
                    gone.add(id);
                } else if (!sameValues(quoted, now)) {
                    changed.add(id);
                }
            });
            var config = inputs.config();
            var items = new HashMap<TypeId, ProjectItem>();
            for (var item : inputs.items()) {
                items.put(new TypeId(item.type()), new ProjectItem(new TypeId(item.type()),
                        enumOf(BlueprintSource.class, item.blueprintSource()), item.me(), item.te(), item.blueprintCostPerRun(),
                        existing(item.facility(), gone), policy(item.policy())));
            }
            var prices = new HashMap<TypeId, PlanPrice>();
            var typed = new HashSet<TypeId>();
            inputs.prices().forEach(price -> {
                prices.put(new TypeId(price.type()), new PlanPrice(price.sell(), price.buy(), price.asOf()));
                if (price.typed()) {
                    typed.add(new TypeId(price.type()));
                }
            });
            var restoredConfig = new ProjectConfig(
                    new ProjectSettings(config.iskPerHour(), config.iskPerCubicMeter(), config.contractFee(),
                            enumOf(QuoteMode.class, config.quoteMode()), config.marginPercent(), config.premiumPercent(),
                            config.quoteStep()),
                    enumOf(PriceSide.class, config.priceSide()), policy(config.defaultPolicy()),
                    existing(config.defaultFacility(), gone),
                    new ProjectBuilder(config.builder().industry(), config.builder().advancedIndustry(),
                            config.builder().implantTimePercent(), skillLevels(inputs)),
                    config.buyerName(), config.note());
            return new Restored(
                    inputs.products().stream().map(product -> new PlanProduct(new TypeId(product.type()), product.quantity())).toList(),
                    new HashSet<>(inputs.boughtParts().stream().map(TypeId::new).toList()), items, prices, typed, restoredConfig, changed, gone);
        } catch (RuntimeException e) {
            throw new UnreadableDocumentException("A stored quote's inputs cannot be restored", e);
        }
    }

    private static List<QuoteInputsV1.Skill> skills(ProjectBuilder builder) {
        return new TreeSet<>(builder.skillLevels().keySet().stream().map(TypeId::value).toList()).stream()
                .map(skill -> new QuoteInputsV1.Skill(skill, builder.level(new TypeId(skill)))).toList();
    }

    /**
     * The builder's skills as the quote has them. An older quote kept them on its items, where two items could hold different levels of one skill: the
     * higher one is taken, as the migration of the projects' own rows does.
     */
    private static Map<TypeId, Integer> skillLevels(QuoteInputsV1 inputs) {
        var levels = new HashMap<TypeId, Integer>();
        Stream.concat(inputs.config().builder().skills().stream(), inputs.items().stream().flatMap(item -> item.skills().stream()))
                .forEach(skill -> levels.merge(new TypeId(skill.skill()), skill.level(), Math::max));
        return levels;
    }

    /** The facility as an id, or null if it is gone. */
    private static FacilityId existing(String id, List<FacilityId> gone) {
        if (id == null) {
            return null;
        }
        var facility = new FacilityId(UUID.fromString(id));
        return gone.contains(facility) ? null : facility;
    }

    private static boolean sameValues(FacilityTerms one, FacilityTerms other) {
        return one.structureMaterialPercent().compareTo(other.structureMaterialPercent()) == 0
                && one.rigMaterialPercent().compareTo(other.rigMaterialPercent()) == 0
                && one.structureTimePercent().compareTo(other.structureTimePercent()) == 0
                && one.rigTimePercent().compareTo(other.rigTimePercent()) == 0
                && one.structureCostPercent().compareTo(other.structureCostPercent()) == 0
                && one.systemCostIndexPercent().compareTo(other.systemCostIndexPercent()) == 0
                && one.taxPercent().compareTo(other.taxPercent()) == 0
                && one.sccSurchargePercent().compareTo(other.sccSurchargePercent()) == 0;
    }

    private static FacilityTerms terms(QuoteInputsV1.FacilityValues values) {
        return new FacilityTerms(values.structureMaterialPercent(), values.rigMaterialPercent(), values.structureTimePercent(),
                values.rigTimePercent(), values.structureCostPercent(), values.systemCostIndexPercent(), values.taxPercent(),
                values.sccSurchargePercent());
    }

    private static QuoteInputsV1.Policy policy(SplitPolicy policy) {
        return policy == null ? null : new QuoteInputsV1.Policy(name(policy.strategy()), policy.maxJobSeconds());
    }

    private static SplitPolicy policy(QuoteInputsV1.Policy policy) {
        return policy == null ? null : new SplitPolicy(enumOf(SplitStrategy.class, policy.strategy()), policy.maxJobSeconds());
    }

    private static String name(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String stored) {
        return Enum.valueOf(type, stored.toUpperCase(Locale.ROOT));
    }
}
