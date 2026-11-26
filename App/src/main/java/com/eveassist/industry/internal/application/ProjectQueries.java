package com.eveassist.industry.internal.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.domain.Project;
import com.eveassist.industry.internal.domain.ProjectItem;
import com.eveassist.sde.ItemType;
import com.eveassist.sde.SdeService;
import com.eveassist.sde.TypeId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reads a project's pages need. They change nothing and never recalculate a costing: costing is on request only, and what these show
 * is what was stored, with whether it may be out of date.
 */
@Service
@PrimaryPort
@Transactional(readOnly = true)
public class ProjectQueries {

    private final ProjectRepository projects;
    private final FacilityRepository facilities;
    private final ProjectCostingRepository costings;
    private final ProjectQuoteRepository quotes;
    private final ProjectRecipes recipes;
    private final SdeService sde;

    ProjectQueries(ProjectRepository projects, FacilityRepository facilities, ProjectCostingRepository costings,
            ProjectQuoteRepository quotes, ProjectRecipes recipes, SdeService sde) {
        this.projects = projects;
        this.facilities = facilities;
        this.costings = costings;
        this.quotes = quotes;
        this.recipes = recipes;
        this.sde = sde;
    }

    /** The viewer's active projects. */
    public List<ProjectOverview> overviews(Viewer viewer) {
        return overviews(viewer, false);
    }

    /** The viewer's projects; with {@code includeArchived} the archived ones too, after the active ones. */
    public List<ProjectOverview> overviews(Viewer viewer, boolean includeArchived) {
        return projects.overviews(viewer, sde.version(), includeArchived);
    }

    /** The viewer's facilities, for choosing one. */
    public List<FacilityOverview> facilities(Viewer viewer) {
        return facilities.list(viewer);
    }

    /** What to build, found by name, for adding a product. */
    public List<ItemType> searchProducts(Viewer viewer, String text) {
        return sde.searchBuildable(text, 12);
    }

    /** Everything the project's page shows. Another account's project looks the same as a missing one. */
    public ProjectDetails details(Viewer viewer, ProjectId id) {
        var project = projects.find(viewer, id).orElseThrow(ProjectNotFoundException::new);
        var meta = projects.meta(viewer, id).orElseThrow(ProjectNotFoundException::new);
        var viewersFacilities = facilities.list(viewer);
        var projectQuotes = quotes.list(viewer, id);
        var costing = costing(viewer, project, viewersFacilities, projectQuotes);
        return new ProjectDetails(id, project.name(), project.version(), project.status(), project.archived(),
                project.lastQuoteNumber(), meta.createdAt(), meta.sourcePlanName(), project.config(),
                project.products().stream().map(product -> new ProjectDetails.ProductLine(product.type().value(),
                        recipes.name(product.type()), product.quantity())).toList(),
                recipes.visibleParts(project).stream().map(type -> new ProjectDetails.PartLine(type.value(), recipes.name(type),
                        project.boughtParts().contains(type))).toList(),
                items(project), viewersFacilities, projectQuotes, costing,
                warningTypeNames(costing), typedPrices(project), skills(project));
    }

    /** The prices the user typed, by the item's name. */
    private List<ProjectDetails.TypedPrice> typedPrices(Project project) {
        return project.typedPrices().stream().map(type -> {
            var price = project.prices().get(type);
            return new ProjectDetails.TypedPrice(type.value(), recipes.name(type), price.sell(), price.asOf());
        }).sorted(java.util.Comparator.comparing(ProjectDetails.TypedPrice::name)).toList();
    }

    /** The names of the types the costing's warnings are about, which can be a material the project does not build. */
    private Map<Integer, String> warningTypeNames(CostingView costing) {
        Map<Integer, String> names = new HashMap<>();
        if (costing != null) {
            for (var warning : costing.costing().document().warnings()) {
                names.computeIfAbsent(warning.type(), type -> recipes.name(new TypeId(type)));
            }
        }
        return names;
    }

    /** Every item the project builds, the products first. An item with nothing chosen for it is shown as it will be costed. */
    private List<ProjectDetails.ItemLine> items(Project project) {
        var lines = new ArrayList<ProjectDetails.ItemLine>();
        for (var type : recipes.builtItems(project)) {
            var chosen = project.items().get(type);
            var item = chosen != null ? chosen : ProjectItem.typed(type);
            lines.add(new ProjectDetails.ItemLine(type.value(), recipes.name(type), recipes.groupName(type), project.hasProduct(type),
                    chosen != null, item.source().name().toLowerCase(java.util.Locale.ROOT), item.me(), item.te(),
                    item.blueprintCostPerRun(), item.facility(), item.policy()));
        }
        return lines;
    }

    /** The time-saving skills that the blueprints of the built items require, each once, by name, with the builder's level. */
    private List<ProjectDetails.SkillLine> skills(Project project) {
        return recipes.builtItems(project).stream().flatMap(type -> recipes.timeSkills(type).stream()).distinct()
                .map(skill -> new ProjectDetails.SkillLine(skill.skill().value(), recipes.name(skill.skill()),
                        project.config().builder().level(skill.skill()), skill.percentPerLevel()))
                .sorted(java.util.Comparator.comparing(ProjectDetails.SkillLine::name))
                .toList();
    }

    /** The Draft's own latest costing with why it may be out of date, or a Quoted project's latest quote's costing, which is frozen. */
    private CostingView costing(Viewer viewer, Project project, List<FacilityOverview> viewersFacilities,
            List<ProjectQuoteRepository.QuoteSummary> projectQuotes) {
        if (!project.draft()) {
            if (projectQuotes.isEmpty()) {
                return null;
            }
            var latest = projectQuotes.getLast().number();
            return quotes.findCosting(viewer, project.id(), latest).map(costing -> new CostingView(costing, true, latest, List.of()))
                    .orElse(null);
        }
        Optional<StoredCosting> stored = costings.findDraft(viewer, project.id());
        return stored.map(costing -> new CostingView(costing, false, 0, staleness(project, costing, viewersFacilities))).orElse(null);
    }

    private List<CostingStaleness> staleness(Project project, StoredCosting costing, List<FacilityOverview> viewersFacilities) {
        var reasons = new ArrayList<CostingStaleness>();
        if (costing.costedProjectVersion() != project.version()) {
            reasons.add(CostingStaleness.PROJECT_EDITED);
        }
        if (!costing.sdeVersion().equals(sde.version())) {
            reasons.add(CostingStaleness.SDE_CHANGED);
        }
        var used = QuoteInputs.facilitiesOf(project);
        if (viewersFacilities.stream().anyMatch(facility -> used.contains(facility.id()) && facility.updatedAt().isAfter(costing.costedAt()))) {
            reasons.add(CostingStaleness.FACILITY_CHANGED);
        }
        return reasons;
    }
}
