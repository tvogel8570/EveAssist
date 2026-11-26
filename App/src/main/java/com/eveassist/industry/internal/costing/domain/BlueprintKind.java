package com.eveassist.industry.internal.costing.domain;

/** What a job's blueprint is, which decides whether the page warns about it. */
public enum BlueprintKind {
    /** A blueprint original: always available, and a project that uses one is told so. */
    ORIGINAL,
    /** A blueprint copy: its runs are limited. */
    COPY,
    /** An ME and TE typed in for a blueprint the developer does not own, or for a what-if. */
    TYPED
}
