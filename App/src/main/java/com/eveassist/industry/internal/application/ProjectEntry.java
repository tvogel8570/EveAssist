package com.eveassist.industry.internal.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.domain.PlanProduct;
import com.eveassist.industry.internal.domain.Project;
import com.eveassist.industry.internal.domain.ProjectLockedException;
import com.eveassist.market.AdjustedPriceService;
import com.eveassist.market.JitaQuotes;
import com.eveassist.market.MarketService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Project actions that may start an ESI fetch. A project's prices change only when the developer asks for it, or when a product is
 * added whose items the project has no price for. The fetch runs between two short transactions, so no database connection is
 * held while ESI is being asked. A quoted project is refused before ESI is asked at all.
 */
@Service
@PrimaryPort
public class ProjectEntry {

    private final ProjectRepository projects;
    private final ProjectRecipes recipes;
    private final ProjectEditing editing;
    private final MarketService market;
    private final AdjustedPriceService adjustedPrices;
    private final ProjectCostings costings;
    private final TransactionTemplate reading;

    ProjectEntry(ProjectRepository projects, ProjectRecipes recipes, ProjectEditing editing, MarketService market,
            AdjustedPriceService adjustedPrices, ProjectCostings costings, PlatformTransactionManager transactions) {
        this.projects = projects;
        this.recipes = recipes;
        this.editing = editing;
        this.market = market;
        this.adjustedPrices = adjustedPrices;
        this.costings = costings;
        this.reading = new TransactionTemplate(transactions);
        this.reading.setReadOnly(true);
    }

    /**
     * Fetches prices for the items the new product brings in that the project has none for, and only those. A product that was
     * removed and is added again was priced out with it, so it is priced again here.
     */
    public ProjectChanged addProduct(Viewer viewer, AddProjectProductCommand command) {
        var project = loadDraft(viewer, command.project());
        if (!recipes.isBuildable(command.type())) {
            throw new UnknownProductException();
        }
        var products = new ArrayList<>(project.products());
        products.add(new PlanProduct(command.type(), command.quantity()));
        var missing = new HashSet<>(recipes.requiredTypes(products));
        missing.removeAll(project.prices().keySet());
        var fetched = missing.isEmpty() ? new JitaQuotes(Map.of(), Map.of()) : market.currentPrices(missing);
        return editing.addProduct(viewer, command, fetched.quotes());
    }

    /** Fetches current prices for everything the project could need. Items ESI cannot answer for keep their old price. */
    public ProjectPriceRefresh refreshPrices(Viewer viewer, ProjectId id) {
        var project = loadDraft(viewer, id);
        var fetched = market.currentPrices(recipes.requiredTypes(project.products()));
        var changed = editing.replacePrices(viewer, id, project.version(), fetched.quotes());
        return new ProjectPriceRefresh(changed, fetched.failed().keySet().stream().map(recipes::name).sorted().toList());
    }

    /**
     * Costs the project, which is the one thing that is on request only. The adjusted prices are refreshed first (a call to ESI only
     * when its hour is up), and if that fails the costing uses the ones that are stored and the result says so. A quoted project and a
     * project with no product are refused before ESI is asked.
     */
    public ProjectCostRun cost(Viewer viewer, ProjectId id) {
        var project = loadDraft(viewer, id);
        if (project.products().isEmpty()) {
            throw new NothingToCostException();
        }
        var refresh = adjustedPrices.refresh();
        return new ProjectCostRun(costings.cost(viewer, id), refresh.problem());
    }

    private Project loadDraft(Viewer viewer, ProjectId id) {
        var project = reading.execute(status -> projects.find(viewer, id)).orElseThrow(ProjectNotFoundException::new);
        if (!project.draft()) {
            throw new ProjectLockedException();
        }
        return project;
    }
}
