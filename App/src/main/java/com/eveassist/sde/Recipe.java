package com.eveassist.sde;

import java.util.List;

/** What one manufacturing run of a blueprint uses, and how many units of the product it makes. */
public record Recipe(TypeId product, int unitsPerRun, List<RecipeMaterial> materials) {

    public Recipe {
        materials = List.copyOf(materials);
    }
}
