package com.eveassist.industry.internal.domain;

import com.eveassist.sde.TypeId;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * What a project needs of one item it builds, and what its jobs make: how the demand for a part came about, for the costing trace.
 *
 * @param unitsNeeded the demand: what the jobs above it use, after their own rounding, and what the buyer gets
 * @param runsNeeded the runs that make those units
 * @param jobs how many jobs the runs were shared into
 * @param unitsMade what the jobs make, which is more than needed when a run makes several units
 */
@ValueObject
public record ItemDemand(TypeId type, long unitsNeeded, long runsNeeded, int jobs, long unitsMade) {
}
