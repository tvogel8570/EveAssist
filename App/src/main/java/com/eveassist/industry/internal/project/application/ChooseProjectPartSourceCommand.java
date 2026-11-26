package com.eveassist.industry.internal.project.application;

import com.eveassist.industry.ProjectId;
import com.eveassist.sde.TypeId;
import org.jmolecules.architecture.cqrs.annotation.Command;

/** @param buy true to buy the part, false to build it */
@Command
public record ChooseProjectPartSourceCommand(ProjectId project, TypeId part, boolean buy, long expectedVersion) {
}
