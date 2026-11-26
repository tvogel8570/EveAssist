package com.eveassist.industry.internal.project.application;

import com.eveassist.industry.ProjectId;
import com.eveassist.sde.TypeId;

/** The user gives up the price they typed for an item of a project, and takes the market price again. */
public record UseMarketPriceCommand(ProjectId project, TypeId type, long expectedVersion) {
}
