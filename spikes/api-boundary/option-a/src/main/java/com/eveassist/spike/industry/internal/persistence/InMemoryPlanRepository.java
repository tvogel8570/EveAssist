package com.eveassist.spike.industry.internal.persistence;

import com.eveassist.spike.industry.PlanId;
import com.eveassist.spike.industry.internal.application.PlanRepository;
import com.eveassist.spike.industry.internal.domain.Plan;
import com.eveassist.spike.industry.internal.domain.PlanLine;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Repository;

@Repository
class InMemoryPlanRepository implements PlanRepository {

    private final Map<PlanId, Plan> plans = Stream.of(
            plan("11111111-1111-1111-1111-111111111111", "alice", "Rifter batch",
                    line("aaaaaaaa-0000-0000-0000-000000000001", "Rifter", 10, 500_000),
                    line("aaaaaaaa-0000-0000-0000-000000000002", "Tritanium", 1_000, 5)),
            plan("22222222-2222-2222-2222-222222222222", "alice", "Ammo run",
                    line("aaaaaaaa-0000-0000-0000-000000000003", "EMP S", 5_000, 20),
                    line("aaaaaaaa-0000-0000-0000-000000000004", "Phased Plasma S", 5_000, 25)),
            plan("33333333-3333-3333-3333-333333333333", "bob", "Bob's secret capital",
                    line("bbbbbbbb-0000-0000-0000-000000000001", "Naglfar", 1, 2_000_000_000)))
            .collect(Collectors.toMap(Plan::id, Function.identity()));

    @Override
    public Optional<Plan> findById(PlanId id) {
        return Optional.ofNullable(plans.get(id));
    }

    @Override
    public List<Plan> findByOwner(String accountId) {
        return plans.values().stream().filter(p -> p.ownerAccountId().equals(accountId)).toList();
    }

    private static Plan plan(String id, String owner, String name, PlanLine... lines) {
        return new Plan(new PlanId(UUID.fromString(id)), owner, name, List.of(lines));
    }

    private static PlanLine line(String id, String item, int quantity, long unitPrice) {
        return new PlanLine(UUID.fromString(id), item, quantity, unitPrice);
    }
}
