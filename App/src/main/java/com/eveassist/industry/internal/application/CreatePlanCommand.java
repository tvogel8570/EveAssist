package com.eveassist.industry.internal.application;

import org.jmolecules.architecture.cqrs.annotation.Command;

@Command
public record CreatePlanCommand(String name) {

    public static final int MAX_NAME_LENGTH = 100;

    /** The name is stripped, and a blank or over-long one is refused, whatever the page allowed. */
    public CreatePlanCommand {
        name = name == null ? "" : name.strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new InvalidPlanNameException();
        }
    }
}
