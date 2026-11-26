package com.eveassist.industry.internal.application;

import com.eveassist.industry.internal.costing.domain.Costing;
import com.eveassist.industry.internal.costing.domain.LineCost;
import com.eveassist.industry.internal.costing.domain.PlanProduct;
import com.eveassist.industry.internal.domain.Project;
import com.eveassist.sde.Recipe;
import com.eveassist.sde.SdeService;
import com.eveassist.sde.TimeSkill;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** What a project's products need, read from the SDE: the items it could use, and which of them are parts. */
@Component
class ProjectRecipes {

    private final SdeService sde;

    ProjectRecipes(SdeService sde) {
        this.sde = sde;
    }

    /**
     * Everything the products could need, bought or built, because an edit can change which. The project keeps a price for each,
     * so that buying or building a part never needs a new one.
     */
    Set<TypeId> requiredTypes(List<PlanProduct> products) {
        return new Costing(sde::recipeFor, type -> Optional.empty()).cost(products).lines().stream()
                .map(LineCost::type).collect(Collectors.toUnmodifiableSet());
    }

    /** True for a part the project shows: it has a blueprint, is not a product, and is not hidden below a part that is bought. */
    boolean isPart(Project project, TypeId type) {
        if (project.hasProduct(type)) {
            return false;
        }
        return new Costing(sde::recipeFor, item -> Optional.empty()).cost(project.products(), project.boughtParts()).lines()
                .stream().anyMatch(line -> line.type().equals(type) && line.buildable());
    }

    /** Only an item with a blueprint can be a product, or have a blueprint chosen for it. */
    boolean isBuildable(TypeId type) {
        return sde.recipeFor(type).isPresent();
    }

    /** What the project builds, in the order costing reaches it: the products, the parts not bought, and the parts of those. */
    Set<TypeId> builtItems(Project project) {
        return new Costing(sde::recipeFor, type -> Optional.empty()).cost(project.products(), project.boughtParts()).lines()
                .stream().filter(LineCost::built).map(LineCost::type)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /** The parts the project shows, each with whether it is bought: items with a blueprint that are not products and not hidden below a part that is bought. */
    List<TypeId> visibleParts(Project project) {
        return new Costing(sde::recipeFor, type -> Optional.empty()).cost(project.products(), project.boughtParts()).lines().stream()
                .filter(line -> line.buildable() && !project.hasProduct(line.type())).map(LineCost::type).toList();
    }

    String groupName(TypeId type) {
        return sde.itemType(type).map(item -> item.groupName()).orElse("");
    }

    Optional<Recipe> recipeFor(TypeId type) {
        return sde.recipeFor(type);
    }

    /** The skills the item's blueprint requires that shorten its time, which the project types the builder's levels for. */
    List<TimeSkill> allTimeSkills() {
        return sde.allTimeSkills();
    }

    List<TimeSkill> timeSkills(TypeId type) {
        return sde.timeSkillsOf(type);
    }

    Optional<BigDecimal> unitVolume(TypeId type) {
        return sde.unitVolume(type);
    }

    String sdeVersion() {
        return sde.version();
    }

    String name(TypeId type) {
        return sde.itemType(type).map(item -> item.name()).orElse("#" + type.value());
    }
}
