package com.eveassist.industry.internal.project.application;

import java.util.List;

/** The project after a price refresh, and the names of the items ESI could not be asked about. Those keep the price they had. */
public record ProjectPriceRefresh(ProjectChanged project, List<String> failedItems) {

    public ProjectPriceRefresh {
        failedItems = List.copyOf(failedItems);
    }
}
