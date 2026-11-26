package com.eveassist.industry.internal.domain;

import com.eveassist.industry.internal.costing.domain.PlanProduct;
import com.eveassist.sde.TypeId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A fully described project: what to make, what to buy instead of build, how each built item is made, and the settings.
 *
 * @param products what the buyer gets
 * @param boughtParts parts that have a recipe but are bought. A product is always built.
 * @param builds how each built item is made. Every item the project builds needs an entry.
 */
@ValueObject
public record ProjectDefinition(
        List<PlanProduct> products, Set<TypeId> boughtParts, Map<TypeId, ProjectBuild> builds,
        ProjectSettings settings) {

    public ProjectDefinition {
        products = List.copyOf(products);
        boughtParts = Set.copyOf(boughtParts);
        builds = Map.copyOf(builds);
        if (settings == null) {
            throw new IllegalArgumentException("A project needs settings");
        }
    }
}
