package com.eveassist.industry.internal.persistence;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.PlanId;
import com.eveassist.industry.internal.application.PlanOverview;
import com.eveassist.industry.internal.application.PlanRepository;
import com.eveassist.industry.internal.application.PlanTotal;
import com.eveassist.industry.internal.domain.Plan;
import com.eveassist.industry.internal.domain.PlanPrice;
import com.eveassist.industry.internal.domain.PlanProduct;
import com.eveassist.sde.TypeId;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPlanRepository implements PlanRepository {

    /**
     * Whether a stored total is out of date. A plan's prices change only when the user asks, so they never make it so.
     * Other inputs to costing, such as skills, join this when they exist.
     */
    private static final String OUT_OF_DATE = """
            (p.costed_plan_version IS DISTINCT FROM p.version OR p.costed_sde_version IS DISTINCT FROM :sde)
            """;

    private final JdbcClient jdbc;

    JdbcPlanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PlanId create(Viewer viewer, String name) {
        var id = UUID.randomUUID();
        jdbc.sql("INSERT INTO plan (public_id, owner_account_id, name) VALUES (:id, :owner, :name)")
                .param("id", id)
                .param("owner", viewer.accountId().value())
                .param("name", name)
                .update();
        return new PlanId(id);
    }

    @Override
    public Optional<Plan> find(Viewer viewer, PlanId id) {
        record Row(long id, String name, long version) {
        }
        var row = jdbc.sql("SELECT id, name, version FROM plan WHERE public_id = :id AND owner_account_id = :owner")
                .param("id", id.value())
                .param("owner", viewer.accountId().value())
                .query(Row.class)
                .optional();
        return row.map(plan -> new Plan(id, plan.name(), plan.version(), products(plan.id()), boughtParts(plan.id()),
                prices(plan.id())));
    }

    private List<PlanProduct> products(long planId) {
        return jdbc.sql("SELECT type_id, quantity FROM plan_product WHERE plan_id = :plan ORDER BY type_id")
                .param("plan", planId)
                .query((rs, row) -> new PlanProduct(new TypeId(rs.getInt("type_id")), rs.getLong("quantity")))
                .list();
    }

    private Map<TypeId, PlanPrice> prices(long planId) {
        var prices = new HashMap<TypeId, PlanPrice>();
        jdbc.sql("SELECT type_id, sell, buy, as_of FROM plan_price WHERE plan_id = :plan")
                .param("plan", planId)
                .query((rs, row) -> prices.put(new TypeId(rs.getInt("type_id")), new PlanPrice(
                        rs.getBigDecimal("sell"), rs.getBigDecimal("buy"), rs.getTimestamp("as_of").toInstant())))
                .list();
        return prices;
    }

    private Set<TypeId> boughtParts(long planId) {
        return Set.copyOf(jdbc.sql("SELECT type_id FROM plan_bought_part WHERE plan_id = :plan")
                .param("plan", planId)
                .query((rs, row) -> new TypeId(rs.getInt("type_id")))
                .list());
    }

    @Override
    public Optional<Long> save(Viewer viewer, Plan plan, PlanTotal total) {
        var pricesAsOf = total.pricesAsOf() == null ? null : Timestamp.from(total.pricesAsOf());
        var updated = jdbc.sql("""
                UPDATE plan SET name = :name, version = version + 1,
                    total_cost = :total, costed_plan_version = version + 1, costed_sde_version = :sde,
                    prices_as_of = :pricesAsOf, costed_at = now()
                WHERE public_id = :id AND owner_account_id = :owner AND version = :version
                RETURNING id, version
                """)
                .param("name", plan.name())
                .param("total", total.totalCost())
                .param("sde", total.sdeVersion())
                .param("pricesAsOf", pricesAsOf, Types.TIMESTAMP)
                .param("id", plan.id().value())
                .param("owner", viewer.accountId().value())
                .param("version", plan.version())
                .query((rs, row) -> new long[] {rs.getLong("id"), rs.getLong("version")})
                .optional();
        updated.ifPresent(row -> {
            saveProducts(row[0], plan.products());
            saveBoughtParts(row[0], plan.boughtParts());
            savePrices(row[0], plan.prices());
        });
        return updated.map(row -> row[1]);
    }

    private void saveProducts(long planId, List<PlanProduct> products) {
        for (var product : products) {
            jdbc.sql("""
                    INSERT INTO plan_product (plan_id, type_id, quantity) VALUES (:plan, :type, :quantity)
                    ON CONFLICT (plan_id, type_id) DO UPDATE SET quantity = excluded.quantity
                    """)
                    .param("plan", planId)
                    .param("type", product.type().value())
                    .param("quantity", product.quantity())
                    .update();
        }
        var keep = products.stream().map(product -> product.type().value()).toList();
        if (keep.isEmpty()) {
            jdbc.sql("DELETE FROM plan_product WHERE plan_id = :plan").param("plan", planId).update();
        } else {
            jdbc.sql("DELETE FROM plan_product WHERE plan_id = :plan AND type_id NOT IN (:keep)")
                    .param("plan", planId)
                    .param("keep", keep)
                    .update();
        }
    }

    private void saveBoughtParts(long planId, Set<TypeId> parts) {
        jdbc.sql("DELETE FROM plan_bought_part WHERE plan_id = :plan").param("plan", planId).update();
        for (var part : parts) {
            jdbc.sql("INSERT INTO plan_bought_part (plan_id, type_id) VALUES (:plan, :type)")
                    .param("plan", planId)
                    .param("type", part.value())
                    .update();
        }
    }

    private void savePrices(long planId, Map<TypeId, PlanPrice> prices) {
        for (var entry : prices.entrySet()) {
            jdbc.sql("""
                    INSERT INTO plan_price (plan_id, type_id, sell, buy, as_of) VALUES (:plan, :type, :sell, :buy, :asOf)
                    ON CONFLICT (plan_id, type_id) DO UPDATE SET sell = excluded.sell, buy = excluded.buy,
                        as_of = excluded.as_of
                    WHERE (plan_price.sell, plan_price.buy, plan_price.as_of)
                        IS DISTINCT FROM (excluded.sell, excluded.buy, excluded.as_of)
                    """)
                    .param("plan", planId)
                    .param("type", entry.getKey().value())
                    .param("sell", entry.getValue().sell())
                    .param("buy", entry.getValue().buy())
                    .param("asOf", Timestamp.from(entry.getValue().asOf()))
                    .update();
        }
        var keep = prices.keySet().stream().map(TypeId::value).toList();
        if (keep.isEmpty()) {
            jdbc.sql("DELETE FROM plan_price WHERE plan_id = :plan").param("plan", planId).update();
        } else {
            jdbc.sql("DELETE FROM plan_price WHERE plan_id = :plan AND type_id NOT IN (:keep)")
                    .param("plan", planId)
                    .param("keep", keep)
                    .update();
        }
    }

    @Override
    public void recordCosting(Viewer viewer, Plan plan, PlanTotal total) {
        var pricesAsOf = total.pricesAsOf() == null ? null : Timestamp.from(total.pricesAsOf());
        jdbc.sql("""
                UPDATE plan p SET total_cost = :total, costed_plan_version = p.version, costed_sde_version = :sde,
                    prices_as_of = :pricesAsOf, costed_at = now()
                WHERE p.public_id = :id AND p.owner_account_id = :owner AND p.version = :version AND
                """ + OUT_OF_DATE + " RETURNING p.id")
                .param("total", total.totalCost())
                .param("sde", total.sdeVersion())
                .param("pricesAsOf", pricesAsOf, Types.TIMESTAMP)
                .param("id", plan.id().value())
                .param("owner", viewer.accountId().value())
                .param("version", plan.version())
                .query(Long.class)
                .optional();
    }

    @Override
    public List<PlanOverview> overviews(Viewer viewer, String currentSdeVersion) {
        return jdbc.sql("""
                SELECT p.public_id, p.name, p.total_cost, p.costed_at,
                    (SELECT count(*) FROM plan_product pp WHERE pp.plan_id = p.id) AS product_count,
                    p.costed_at IS NOT NULL AND
                """ + OUT_OF_DATE + """
                    AS out_of_date
                FROM plan p
                WHERE p.owner_account_id = :owner
                ORDER BY p.name, p.public_id
                """)
                .param("owner", viewer.accountId().value())
                .param("sde", currentSdeVersion)
                .query((rs, row) -> new PlanOverview(
                        new PlanId(rs.getObject("public_id", UUID.class)),
                        rs.getString("name"),
                        rs.getLong("product_count"),
                        rs.getBigDecimal("total_cost"),
                        rs.getTimestamp("costed_at") == null ? null : rs.getTimestamp("costed_at").toInstant(),
                        rs.getBoolean("out_of_date")))
                .list();
    }
}
