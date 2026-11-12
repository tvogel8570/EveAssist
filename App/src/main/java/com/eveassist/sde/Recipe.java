package com.eveassist.sde;

import java.util.List;

/**
 * What one manufacturing run of a blueprint uses at ME 0, how many units of the product it makes, and how long it takes
 * at TE 0.
 */
public record Recipe(TypeId product, int unitsPerRun, int secondsPerRun, List<RecipeMaterial> materials) {

    public Recipe {
        materials = List.copyOf(materials);
    }
}
