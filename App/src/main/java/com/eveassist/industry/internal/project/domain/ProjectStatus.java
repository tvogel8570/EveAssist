package com.eveassist.industry.internal.project.domain;

/** A project is a Draft, which can be edited, until a quote is issued, and then it is Quoted and locked. */
public enum ProjectStatus {
    DRAFT,
    QUOTED
}
