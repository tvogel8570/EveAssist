package com.eveassist.industry.internal.application;

/** Why a Draft's stored costing may no longer be what costing it now would give. A frozen quote's costing is never out of date. */
public enum CostingStaleness {
    /** The project was edited after it was costed. */
    PROJECT_EDITED,
    /** The SDE was imported again after it was costed. */
    SDE_CHANGED,
    /** A facility the project uses was changed after it was costed. */
    FACILITY_CHANGED
}
