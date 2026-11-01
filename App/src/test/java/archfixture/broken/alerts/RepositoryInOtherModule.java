package archfixture.broken.alerts;

import archfixture.broken.industry.internal.application.PlanRepository;

@org.springframework.stereotype.Component
public class RepositoryInOtherModule {
    PlanRepository plans;
}
