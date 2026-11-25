package com.eveassist.industry.internal.application;

import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.domain.ProjectConfig;
import org.jmolecules.architecture.cqrs.annotation.Command;

/** Sets the rates, the quote, the price side, the split policy, the default facility, the builder and the buyer in one go. */
@Command
public record SetProjectConfigCommand(ProjectId project, ProjectConfig config, long expectedVersion) {
}
