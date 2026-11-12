package com.eveassist.industry.internal.persistence;

import com.eveassist.identity.AccountDataSource;
import com.eveassist.identity.AccountId;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The account's plans with their products and the parts they buy, for the export. Costings are derived and are left out. */
@Component
class PlansAccountData implements AccountDataSource {

    private final JdbcClient jdbc;

    PlansAccountData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, List<Map<String, Object>>> sections(AccountId account) {
        var plans = jdbc.sql("""
                SELECT id, name, created_at, archived_at FROM plan WHERE owner_account_id = :owner ORDER BY name, id
                """)
                .param("owner", account.value())
                .query((rs, row) -> {
                    var plan = new LinkedHashMap<String, Object>();
                    plan.put("id", rs.getLong("id"));
                    plan.put("name", rs.getString("name"));
                    plan.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                    Timestamp archived = rs.getTimestamp("archived_at");
                    plan.put("archivedAt", archived == null ? null : archived.toInstant().toString());
                    return plan;
                }).list();
        var rows = new ArrayList<Map<String, Object>>();
        for (var plan : plans) {
            var id = (Long) plan.remove("id");
            plan.put("products", typeRows(id, "plan_product", "quantity"));
            plan.put("boughtParts", typeRows(id, "plan_bought_part", null));
            rows.add(plan);
        }
        return Map.of("plans", rows);
    }

    private List<Map<String, Object>> typeRows(long planId, String table, String quantityColumn) {
        var quantity = quantityColumn == null ? "" : ", p." + quantityColumn + " AS quantity";
        return jdbc.sql("SELECT p.type_id, t.\"typeName\" AS name" + quantity + " FROM " + table
                + " p LEFT JOIN sde.\"invTypes\" t ON t.\"typeID\" = p.type_id WHERE p.plan_id = :plan ORDER BY p.type_id")
                .param("plan", planId)
                .query((rs, row) -> {
                    var line = new LinkedHashMap<String, Object>();
                    line.put("typeId", rs.getInt("type_id"));
                    line.put("name", rs.getString("name"));
                    if (quantityColumn != null) {
                        line.put("quantity", rs.getLong("quantity"));
                    }
                    return (Map<String, Object>) line;
                }).list();
    }
}
