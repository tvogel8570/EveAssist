package com.eveassist.industry.internal.project.domain;

/** A quoted project cannot be edited. A new version from a quote makes it a Draft again. */
public class ProjectLockedException extends RuntimeException {

    public ProjectLockedException() {
        super("A quoted project cannot be edited");
    }
}
