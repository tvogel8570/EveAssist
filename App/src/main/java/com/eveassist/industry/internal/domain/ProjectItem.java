package com.eveassist.industry.internal.domain;

import com.eveassist.industry.FacilityId;
import com.eveassist.industry.internal.costing.domain.Costing;
import com.eveassist.industry.internal.costing.domain.SplitPolicy;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * How one built item is made in a project.
 *
 * @param type the item
 * @param source where its blueprint comes from. Typed is the first form, owned blueprints come with the inventory
 * @param me the typed blueprint's material efficiency, 0 to 10
 * @param te the typed blueprint's time efficiency, 0 to 20
 * @param blueprintCostPerRun the price per run of a bought copy, or the depreciation of an original (0 for none)
 * @param facility the facility the item is built in, or null for the project's default
 * @param policy how its runs are split, or null for the project's own
 */
@ValueObject
public record ProjectItem(
        TypeId type, BlueprintSource source, int me, int te, BigDecimal blueprintCostPerRun, FacilityId facility,
        SplitPolicy policy) {

    public ProjectItem {
        if (type == null || source == null || blueprintCostPerRun == null || blueprintCostPerRun.signum() < 0) {
            throw new IllegalArgumentException("An item needs a type, a source and a blueprint cost of 0 or more");
        }
        if (me < 0 || me > 10 || te < 0 || te > 20) {
            throw new IllegalArgumentException("ME is from 0 to 10 and TE from 0 to 20, was " + me + " and " + te);
        }
    }

    /** An item at the plan's ME 10 and TE 20, in the project's default facility, with nothing else chosen. */
    public static ProjectItem typed(TypeId type) {
        return new ProjectItem(type, BlueprintSource.TYPED, Costing.ME, Costing.TE, BigDecimal.ZERO, null, null);
    }
}
