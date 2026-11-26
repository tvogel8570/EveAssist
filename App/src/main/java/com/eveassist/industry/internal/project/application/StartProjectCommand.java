package com.eveassist.industry.internal.project.application;

import com.eveassist.industry.PlanId;
import org.jmolecules.architecture.cqrs.annotation.Command;

/** @param name the project's name, or null to take the plan's */
@Command
public record StartProjectCommand(PlanId plan, String name) {

    public static final int MAX_NAME_LENGTH = 100;

    /** A name is stripped, and a blank or over-long one is refused, whatever the page allowed. */
    public StartProjectCommand {
        if (name != null) {
            name = name.strip();
            if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
                throw new InvalidProjectNameException();
            }
        }
    }
}
