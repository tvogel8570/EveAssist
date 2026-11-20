package com.eveassist.industry.internal.application;

import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.domain.ProjectItem;
import org.jmolecules.architecture.cqrs.annotation.Command;

/** Sets how one built item is made, replacing what the project had for it. */
@Command
public record SetProjectItemCommand(ProjectId project, ProjectItem item, long expectedVersion) {
}
