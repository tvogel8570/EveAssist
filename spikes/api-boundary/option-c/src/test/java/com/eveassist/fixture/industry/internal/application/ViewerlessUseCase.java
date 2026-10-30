package com.eveassist.fixture.industry.internal.application;

import com.eveassist.spike.industry.PlanId;
import org.springframework.stereotype.Service;

/** Breaks the rule on purpose. Outside the app's base package, so it never becomes a bean. */
@Service
public class ViewerlessUseCase {
    public String name(PlanId id) {
        return id.toString();
    }
}
