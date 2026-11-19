package com.eveassist.industry.internal.application;

import com.eveassist.industry.ProjectId;
import org.jmolecules.architecture.cqrs.annotation.Command;

/** Makes the project a Draft again, starting as a copy of one of its quotes. The quote stays as it was sent. */
@Command
public record NewVersionCommand(ProjectId project, int number, long expectedVersion) {
}
