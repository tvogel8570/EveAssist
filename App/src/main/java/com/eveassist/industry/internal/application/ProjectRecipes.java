package com.eveassist.industry.internal.application;

import com.eveassist.industry.internal.domain.Costing;
import com.eveassist.industry.internal.domain.LineCost;
import com.eveassist.industry.internal.domain.PlanProduct;
import com.eveassist.industry.internal.domain.Project;
import com.eveassist.sde.SdeService;
import com.eveassist.sde.TypeId;
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

    String name(TypeId type) {
        return sde.itemType(type).map(item -> item.name()).orElse("#" + type.value());
    }
}
