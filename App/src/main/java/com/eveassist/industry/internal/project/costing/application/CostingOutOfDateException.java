package com.eveassist.industry.internal.project.costing.application;

/** The project has no stored costing for the version it is at now, so a quote cannot be issued from it. Cost it again. */
public class CostingOutOfDateException extends RuntimeException {
}
