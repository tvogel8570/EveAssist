package archfixture.broken.industry.internal.application;

import archfixture.broken.identity.ReadsNoAccountData;

@ReadsNoAccountData
public class MarkedButReadingPlans {
    PlanRepository plans;

    public void load() {}
}
