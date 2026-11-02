package com.eveassist.industry.internal.application;

import com.eveassist.industry.internal.domain.Costing;
import com.eveassist.industry.internal.domain.PlanProduct;
import com.eveassist.sde.SdeService;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Times costing on large plans against the real SDE in the dev database. It reports rather than asserts, and only runs
 * when asked: ./mvnw verify -Dit.test=RealSdeMeasurementIT -Deveassist.measure=true -Dsurefire.skip=true
 */
@SpringBootTest
@ActiveProfiles("measure")
@EnabledIfSystemProperty(named = "eveassist.measure", matches = "true")
class RealSdeMeasurementIT {

    @Autowired
    SdeService sde;

    @Autowired
    JdbcClient jdbc;

    @Test
    void costingLargePlans() {
        var titans = types("SELECT t.\"typeID\" FROM sde.\"invTypes\" t JOIN sde.\"invGroups\" g USING (\"groupID\") "
                + "WHERE t.published AND g.\"groupName\" = 'Titan'");
        var capitals = types("SELECT t.\"typeID\" FROM sde.\"invTypes\" t JOIN sde.\"invGroups\" g USING (\"groupID\") "
                + "WHERE t.published AND g.\"groupName\" IN ('Titan', 'Supercarrier', 'Dreadnought', 'Carrier', "
                + "'Force Auxiliary', 'Capital Industrial Ship', 'Jump Freighter', 'Freighter')");
        var everything = types("SELECT p.\"productTypeID\" FROM sde.\"industryActivityProducts\" p "
                + "JOIN sde.\"invTypes\" t ON t.\"typeID\" = p.\"typeID\" AND t.published WHERE p.\"activityID\" = 1");
        report("One Titan-class ship", titans.subList(0, 1));
        report("All Titans (" + titans.size() + ")", titans);
        report("All capitals (" + capitals.size() + ")", capitals);
        report("Everything buildable (" + everything.size() + ")", everything);
    }

    private List<TypeId> types(String sql) {
        return jdbc.sql(sql).query(Integer.class).list().stream().distinct().map(TypeId::new).toList();
    }

    private void report(String name, List<TypeId> products) {
        var plan = products.stream().map(type -> new PlanProduct(type, 3)).toList();
        var costing = new Costing(sde::recipeFor, type -> Optional.of(BigDecimal.TEN));
        for (int warmup = 0; warmup < 200; warmup++) {
            costing.cost(plan);
        }
        var edited = new ArrayList<>(plan);
        edited.set(0, new PlanProduct(products.getFirst(), 4));
        var times = new ArrayList<Long>();
        int lines = 0;
        int changed = 0;
        for (int run = 0; run < 200; run++) {
            long start = System.nanoTime();
            var before = costing.cost(plan);
            var after = costing.cost(edited);
            changed = after.changedSince(before).size();
            times.add(System.nanoTime() - start);
            lines = after.lines().size();
        }
        times.sort(Long::compare);
        System.out.printf("MEASURE %s: %d lines, %d changed by one edit, two costings and compare: median %.2f ms, "
                + "p95 %.2f ms%n", name, lines, changed, times.get(100) / 1e6, times.get(190) / 1e6);
    }
}
