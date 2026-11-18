package com.eveassist.industry.internal.domain;

import com.eveassist.sde.RecipeMaterial;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * What one manufacturing job uses and costs, before any material is priced.
 *
 * @param product what the job makes
 * @param runs the runs in the job
 * @param unitsMade the units those runs make
 * @param materials the units of each material the whole job uses
 * @param seconds how long the job takes
 * @param estimatedItemValue the value the install cost is worked out from, taken from the blueprint's base materials
 * @param installCost the job fee, facility tax and SCC surcharge together
 * @param unpricedMaterials how many base materials had no adjusted price, so the value and the cost are too low
 */
@ValueObject
public record JobCost(
        TypeId product, long runs, long unitsMade, List<RecipeMaterial> materials, long seconds,
        BigDecimal estimatedItemValue, BigDecimal installCost, int unpricedMaterials) {

    public JobCost {
        materials = List.copyOf(materials);
    }
}
