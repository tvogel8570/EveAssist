package com.eveassist.industry.internal.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.costing.domain.PlanPrice;
import com.eveassist.industry.internal.domain.Project;
import com.eveassist.industry.internal.shared.application.UnknownProductException;
import com.eveassist.market.JitaQuote;
import com.eveassist.sde.TypeId;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The edits of a project. Each loads the project at the version the caller saw, applies one change and saves it, and a project that
 * has changed since is refused. A quoted project refuses every edit of its inputs ({@code ProjectLockedException}). Costing is not
 * done here: it is on request only. Prices come in already fetched, because a fetch from ESI must not hold a transaction open, so
 * see {@link ProjectEntry} for the calls that ask ESI.
 */
@Service
@PrimaryPort
@Transactional
public class ProjectEditing {

    private final ProjectRepository projects;
    private final PlanRepository plans;
    private final ProjectRecipes recipes;

    private final ProjectDefaultsRepository defaults;

    ProjectEditing(ProjectRepository projects, PlanRepository plans, ProjectRecipes recipes, ProjectDefaultsRepository defaults) {
        this.defaults = defaults;
        this.projects = projects;
        this.plans = plans;
        this.recipes = recipes;
    }

    /**
     * Copies a plan into a new Draft: its products, the parts it buys and its prices, with the settings of the account's project defaults. After this the
     * project is its own, and the plan or the defaults can change or go without reaching it.
     */
    public ProjectChanged start(Viewer viewer, StartProjectCommand command) {
        var plan = plans.find(viewer, command.plan()).orElseThrow(PlanNotFoundException::new);
        var id = projects.create(viewer, command.name() == null ? plan.name() : command.name(), plan.id());
        var project = load(viewer, id);
        defaults.find(viewer).ifPresent(project::startWith);
        plan.products().forEach(product -> project.addProduct(product.type(), product.quantity()));
        plan.boughtParts().forEach(project::buyPart);
        plan.prices().forEach(project::setPrice);
        return new ProjectChanged(id, save(viewer, project));
    }

    /** A rename is not an edit of the inputs: it works on a quoted project and keeps the version. */
    public void rename(Viewer viewer, RenameProjectCommand command) {
        if (!projects.rename(viewer, command.project(), command.name())) {
            throw new ProjectNotFoundException();
        }
    }

    /** Hides the project from the list. It can be restored, and opened meanwhile. Allowed in either state. */
    public void archive(Viewer viewer, ProjectId id) {
        if (!projects.setArchived(viewer, id, true)) {
            throw new ProjectNotFoundException();
        }
    }

    public void restore(Viewer viewer, ProjectId id) {
        if (!projects.setArchived(viewer, id, false)) {
            throw new ProjectNotFoundException();
        }
    }

    /** Deletes the project for good, with its quotes. Archiving is the way to keep it. */
    public void delete(Viewer viewer, ProjectId id) {
        if (!projects.delete(viewer, id)) {
            throw new ProjectNotFoundException();
        }
    }

    /** Adds the product with no new prices, so an item the project has no price for stays unpriced. */
    public ProjectChanged addProduct(Viewer viewer, AddProjectProductCommand command) {
        return addProduct(viewer, command, Map.of());
    }

    /**
     * Adds the product, which may be one the plan did not have, and takes the given prices for items the project has none for. The
     * prices the project already has are not touched, so the rest of the costing stays as it was.
     */
    public ProjectChanged addProduct(Viewer viewer, AddProjectProductCommand command, Map<TypeId, JitaQuote> newPrices) {
        var project = loadAt(viewer, command.project(), command.expectedVersion());
        if (!recipes.isBuildable(command.type())) {
            throw new UnknownProductException();
        }
        newPrices.forEach((type, quote) -> project.addPriceIfMissing(type, priceOf(quote)));
        project.addProduct(command.type(), command.quantity());
        return new ProjectChanged(project.id(), save(viewer, project));
    }

    public ProjectChanged changeQuantity(Viewer viewer, ChangeProjectQuantityCommand command) {
        var project = loadAt(viewer, command.project(), command.expectedVersion());
        if (!project.hasProduct(command.type())) {
            throw new ProjectNotFoundException();
        }
        project.changeQuantity(command.type(), command.quantity());
        return new ProjectChanged(project.id(), save(viewer, project));
    }

    /**
     * Takes the product out and drops the prices and the items that nothing needs any more, so that adding it back is priced
     * again at once (the developer decided so on 2026-11-18). What other products still need is kept as it was.
     */
    public ProjectChanged removeProduct(Viewer viewer, RemoveProjectProductCommand command) {
        var project = loadAt(viewer, command.project(), command.expectedVersion());
        if (!project.hasProduct(command.type())) {
            throw new ProjectNotFoundException();
        }
        project.removeProduct(command.type());
        var needed = recipes.requiredTypes(project.products());
        project.keepPricesFor(needed);
        project.keepItemsFor(needed);
        return new ProjectChanged(project.id(), save(viewer, project));
    }

    /**
     * Takes new prices for the project, which is the only way its existing prices change. An item with no new price keeps its old
     * one, and prices of items nothing needs are dropped.
     */
    public ProjectChanged replacePrices(Viewer viewer, ProjectId id, long expectedVersion, Map<TypeId, JitaQuote> prices) {
        var project = loadAt(viewer, id, expectedVersion);
        // a price the user typed is not replaced by the market's
        prices.forEach((type, quote) -> project.setMarketPrice(type, priceOf(quote)));
        project.keepPricesFor(recipes.requiredTypes(project.products()));
        return new ProjectChanged(project.id(), save(viewer, project));
    }

    /**
     * Takes the price the user typed for an item, in place of the market's, so that a project can be costed (and quoted) with an item the market has no price for,
     * or too little of one for the price basis. It stays until the user asks for the market price again, and a refresh of the prices leaves it. Only an item the
     * project prices can be given one. It is an edit, so the version rises and a costing made before is out of date.
     */
    public ProjectChanged typePrice(Viewer viewer, TypePriceCommand command) {
        var project = loadAt(viewer, command.project(), command.expectedVersion());
        if (!recipes.requiredTypes(project.products()).contains(command.type())) {
            throw new PriceNotNeededException();
        }
        project.typePrice(command.type(), command.price(), java.time.Instant.now());
        return new ProjectChanged(project.id(), save(viewer, project));
    }

    /** Takes the market's price for an item in place of the one the user typed, or no price when the market has none. {@code quote} is what {@link ProjectEntry} fetched. */
    public ProjectChanged useMarketPrice(Viewer viewer, UseMarketPriceCommand command, java.util.Optional<JitaQuote> quote) {
        var project = loadAt(viewer, command.project(), command.expectedVersion());
        project.useMarketPrice(command.type(), quote.map(ProjectEditing::priceOf).orElse(null));
        return new ProjectChanged(project.id(), save(viewer, project));
    }

    /** Only a part the project shows can be chosen, so a part hidden below one that is bought is refused. */
    public ProjectChanged choosePartSource(Viewer viewer, ChooseProjectPartSourceCommand command) {
        var project = loadAt(viewer, command.project(), command.expectedVersion());
        if (!recipes.isPart(project, command.part())) {
            throw new ProjectNotFoundException();
        }
        if (command.buy()) {
            project.buyPart(command.part());
        } else {
            project.buildPart(command.part());
        }
        return new ProjectChanged(project.id(), save(viewer, project));
    }

    /** Sets how one item is made. The item must be something the project's products could need and that has a blueprint. */
    public ProjectChanged setItem(Viewer viewer, SetProjectItemCommand command) {
        var project = loadAt(viewer, command.project(), command.expectedVersion());
        var type = command.item().type();
        if (!recipes.isBuildable(type) || !recipes.requiredTypes(project.products()).contains(type)) {
            throw new ProjectNotFoundException();
        }
        project.setItem(command.item());
        return new ProjectChanged(project.id(), save(viewer, project));
    }

    /** Sets the rates, the quote, the price side, the split policy, the default facility, the builder and the buyer. */
    public ProjectChanged setConfig(Viewer viewer, SetProjectConfigCommand command) {
        var project = loadAt(viewer, command.project(), command.expectedVersion());
        var config = command.config();
        project.settings(config.settings());
        project.priceSide(config.priceSide());
        project.defaultPolicy(config.defaultPolicy());
        project.defaultFacility(config.defaultFacility());
        project.builder(config.builder());
        project.buyer(config.buyerName(), config.note());
        return new ProjectChanged(project.id(), save(viewer, project));
    }

    private static PlanPrice priceOf(JitaQuote quote) {
        return new PlanPrice(quote.sell(), quote.buy(), quote.asOf());
    }

    private Project loadAt(Viewer viewer, ProjectId id, long expectedVersion) {
        var project = load(viewer, id);
        if (project.version() != expectedVersion) {
            throw new ProjectChangedException();
        }
        return project;
    }

    private Project load(Viewer viewer, ProjectId id) {
        return projects.find(viewer, id).orElseThrow(ProjectNotFoundException::new);
    }

    private long save(Viewer viewer, Project project) {
        return projects.save(viewer, project).orElseThrow(ProjectChangedException::new);
    }
}
