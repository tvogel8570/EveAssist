package com.eveassist.industry.internal.application;

import com.eveassist.industry.ProjectId;
import org.jmolecules.architecture.cqrs.annotation.Command;

@Command
public record RenameProjectCommand(ProjectId project, String name) {

    public RenameProjectCommand {
        name = name == null ? "" : name.strip();
        if (name.isEmpty() || name.length() > StartProjectCommand.MAX_NAME_LENGTH) {
            throw new InvalidProjectNameException();
        }
    }
}
