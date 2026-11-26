package com.eveassist.industry.internal.application;

import static com.eveassist.IntegrationTest.COMPONENT;
import static com.eveassist.IntegrationTest.HAULER;
import static com.eveassist.IntegrationTest.PILOTS_MINERAL;
import static com.eveassist.IntegrationTest.TRITANIUM;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.FacilityId;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.domain.BlueprintSource;
import com.eveassist.industry.internal.domain.ProjectBuilder;
import com.eveassist.industry.internal.domain.ProjectConfig;
import com.eveassist.industry.internal.domain.ProjectItem;
import com.eveassist.market.JitaQuote;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The scenario that several integration tests cost by hand: two Test Haulers in an Azbel (materials -1%, time -20%, job fee -4%, an index
 * of 2%, SCC 4%), built by someone with Industry V and Advanced Industry V, the Hauler's blueprint also saving time with Nanite
 * Engineering V and Mechanical Engineering III. With the adjusted prices Tritanium 4, Pilot's Mineral 8 and Test Component 100, it costs
 * 11,344.984 ISK and a 20% margin quote rounds up to 15,000 ISK (the arithmetic is in {@code ProjectCostingsIT}).
 */
public final class TwoHaulers {

    static final TypeId NANITE = new TypeId(11442);
    static final TypeId MECHANICAL = new TypeId(11452);
    private static final Instant AS_OF = Instant.parse("2026-11-18T12:00:00Z");

    private final ProjectEditing editing;
    private final ProjectRepository projects;
    private final JdbcClient jdbc;

    public TwoHaulers(ProjectEditing editing, ProjectRepository projects, JdbcClient jdbc) {
        this.editing = editing;
        this.projects = projects;
        this.jdbc = jdbc;
    }

    public FacilityId azbel(Viewer owner, String index) {
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO facility (public_id, owner_account_id, name, structure_material_percent, structure_time_percent,
                    structure_cost_percent, system_cost_index_percent, scc_surcharge_percent)
                VALUES (:id, :owner, 'Azbel', 1, 20, 4, :index, 4)
                """)
                .param("id", id).param("owner", owner.accountId().value()).param("index", new BigDecimal(index)).update();
        return new FacilityId(id);
    }

    private static JitaQuote quote(TypeId type, String sell, String buy) {
        return new JitaQuote(type, new BigDecimal(sell), new BigDecimal(buy), AS_OF);
    }

    /** Two Haulers, priced, in a Draft with the builder above and the given facility as the default (or none). */
    public ProjectId project(Viewer owner, FacilityId facility) {
        return project(owner, facility, "Two Haulers", null);
    }

    /** The same, with a name, started from a plan (or none), which a page shows. */
    public ProjectId project(Viewer owner, FacilityId facility, String name, com.eveassist.industry.PlanId plan) {
        var id = projects.create(owner, name, plan);
        var version = editing.addProduct(owner, new AddProjectProductCommand(id, HAULER, 2, 0), Map.of(
                TRITANIUM, quote(TRITANIUM, "5", "4"), PILOTS_MINERAL, quote(PILOTS_MINERAL, "10", "8"))).version();
        version = editing.setItem(owner, new SetProjectItemCommand(id, new ProjectItem(HAULER, BlueprintSource.TYPED, 10, 20,
                BigDecimal.ZERO, null, null), version)).version();
        editing.setConfig(owner, new SetProjectConfigCommand(id, ProjectConfig.defaults()
                .withBuilder(new ProjectBuilder(5, 5, BigDecimal.ZERO, Map.of(NANITE, 5, MECHANICAL, 3))).withDefaultFacility(facility), version));
        return id;
    }
}
