package com.eveassist.spike.industry.internal.application;

import com.eveassist.spike.industry.PlanId;
import com.eveassist.spike.industry.internal.domain.Plan;
import java.util.List;
import java.util.Optional;

public interface PlanRepository {
    Optional<Plan> findById(PlanId id);

    List<Plan> findByOwner(String accountId);
}
