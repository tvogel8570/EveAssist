package com.eveassist.industry.internal.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.FacilityId;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.domain.BlueprintSource;
import com.eveassist.industry.internal.domain.BuilderTerms;
import com.eveassist.industry.internal.domain.FacilityTerms;
import com.eveassist.industry.internal.domain.JobTerms;
import com.eveassist.industry.internal.domain.PlanPrice;
import com.eveassist.industry.internal.domain.PriceSide;
import com.eveassist.industry.internal.domain.ProductSkill;
import com.eveassist.industry.internal.domain.ProjectBuild;
import com.eveassist.industry.internal.domain.ProjectCosting;
import com.eveassist.industry.internal.domain.ProjectDefinition;
import com.eveassist.industry.internal.domain.ProjectItem;
import com.eveassist.industry.internal.domain.ProjectLockedException;
import com.eveassist.industry.internal.domain.ProjectWarning;
import com.eveassist.industry.internal.domain.ProjectWarning.Kind;
import com.eveassist.industry.internal.domain.ProjectCost;
import com.eveassist.market.AdjustedPriceService;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Costs a Draft on request: it reads the project, the facilities it uses, the SDE and the adjusted prices, hands them to
 * {@link ProjectCosting} and gives the result back. It writes nothing, and costing happens only when asked for (decided on
 * 2026-11-18), never as the side effect of an edit or of opening a project.
 *
 * <p>A project costs with its own prices, at its own price side, and never with anything fresher. The adjusted prices, which the install
 * cost depends on, are CCP's and shared: they are the ones stored when this is called, and {@link ProjectEntry} refreshes them first
 * when it is asked to cost. The result says how old they are. An item with no facility chosen is
 * costed with none, and the result says so, because its install cost is then missing and no quote may be issued from it.
 */
@Service
@PrimaryPort
@Transactional(readOnly = true)
public class ProjectCostings {

    private final ProjectRepository projects;
    private final ProjectRecipes recipes;
    private final FacilityTermsLookup facilities;
    private final AdjustedPriceService adjustedPrices;

    ProjectCostings(ProjectRepository projects, ProjectRecipes recipes, FacilityTermsLookup facilities,
            AdjustedPriceService adjustedPrices) {
        this.projects = projects;
        this.recipes = recipes;
        this.facilities = facilities;
        this.adjustedPrices = adjustedPrices;
    }

    public ProjectCostResult cost(Viewer viewer, ProjectId id) {
        var project = projects.find(viewer, id).orElseThrow(ProjectNotFoundException::new);
        if (!project.draft()) {
            throw new ProjectLockedException();
        }
        if (project.products().isEmpty()) {
            throw new NothingToCostException();
        }
        var config = project.config();
        Map<TypeId, ProjectBuild> builds = new HashMap<>();
        var noFacility = new ArrayList<ProjectWarning>();
        Map<FacilityId, FacilityTerms> read = new HashMap<>();
        for (var type : recipes.builtItems(project)) {
            var item = project.items().getOrDefault(type, ProjectItem.typed(type));
            if (item.source() == BlueprintSource.OWNED) {
                throw new OwnedBlueprintsNotAvailableException();
            }
            var facilityId = item.facility() != null ? item.facility() : config.defaultFacility();
            FacilityTerms facility = FacilityTerms.NONE;
            if (facilityId == null) {
                noFacility.add(new ProjectWarning(Kind.NO_FACILITY, type));
            } else {
                facility = read.computeIfAbsent(facilityId,
                        key -> facilities.termsOf(viewer, key).orElseThrow(FacilityNotFoundException::new));
            }
            var builder = new BuilderTerms(config.builder().industry(), config.builder().advancedIndustry(), productSkills(item),
                    config.builder().implantTimePercent());
            var policy = item.policy() != null ? item.policy() : config.defaultPolicy();
            builds.put(type, new ProjectBuild.Typed(new JobTerms(item.me(), item.te(), facility, builder),
                    item.blueprintCostPerRun(), policy));
        }
        var prices = project.prices();
        var side = config.priceSide();
        var adjusted = adjustedPrices.of(recipes.requiredTypes(project.products()));
        var costing = new ProjectCosting(recipes::recipeFor, type -> unitPrice(prices.get(type), side),
                type -> Optional.ofNullable(adjusted.get(type)), recipes::unitVolume);
        var comparison = costing.compareToLeastCost(
                new ProjectDefinition(project.products(), project.boughtParts(), builds, config.settings()));
        var warnings = new ArrayList<>(comparison.chosen().warnings());
        warnings.addAll(noFacility);
        return new ProjectCostResult(id, project.version(), recipes.sdeVersion(), oldestBoughtPrice(comparison.chosen(), prices),
                adjustedPrices.asOf().orElse(null), comparison, warnings);
    }

    /** The builder's level in each skill the item's blueprint requires that saves time. A skill the developer did not type is level 0. */
    private java.util.List<ProductSkill> productSkills(ProjectItem item) {
        return recipes.timeSkills(item.type()).stream()
                .map(skill -> new ProductSkill(item.skillLevels().getOrDefault(skill.skill(), 0), skill.percentPerLevel()))
                .toList();
    }

    private static Optional<BigDecimal> unitPrice(PlanPrice price, PriceSide side) {
        if (price == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(side == PriceSide.SELL ? price.sell() : price.buy());
    }

    /** The oldest price among the items that are bought, which is when the costing's prices were last taken. */
    private static Instant oldestBoughtPrice(ProjectCost cost, Map<TypeId, PlanPrice> prices) {
        return cost.bought().stream().map(item -> prices.get(item.type())).filter(Objects::nonNull)
                .map(PlanPrice::asOf).min(Instant::compareTo).orElse(null);
    }
}
