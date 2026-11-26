package com.eveassist.industry.internal.project.application;

import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.project.domain.ProjectItem;
import org.jmolecules.architecture.cqrs.annotation.Command;

/** Sets how one built item is made, replacing what the project had for it. */
@Command
public record SetProjectItemCommand(ProjectId project, ProjectItem item, long expectedVersion) {
}
