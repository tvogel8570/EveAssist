package com.eveassist.industry.internal.domain;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Everything besides the recipe that decides what one manufacturing job uses, how long it takes and what it costs: the
 * blueprint's levels, the facility and the builder.
 *
 * @param me the blueprint's material efficiency, 0 to 10
 * @param te the blueprint's time efficiency, 0 to 20
 */
@ValueObject
public record JobTerms(int me, int te, FacilityTerms facility, BuilderTerms builder) {

    /** What a plan assumes for every blueprint: ME 10 and TE 20, in no facility and with no builder's help. */
    public static final JobTerms PLAN = new JobTerms(10, 20, FacilityTerms.NONE, BuilderTerms.NONE);

    public JobTerms {
        if (me < 0 || me > 10) {
            throw new IllegalArgumentException("ME is from 0 to 10, was " + me);
        }
        if (te < 0 || te > 20) {
            throw new IllegalArgumentException("TE is from 0 to 20, was " + te);
        }
        if (facility == null || builder == null) {
            throw new IllegalArgumentException("A job needs a facility and a builder");
        }
    }
}
