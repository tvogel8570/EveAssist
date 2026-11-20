package com.eveassist.industry.internal.persistence;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.FacilityId;
import com.eveassist.industry.internal.application.FacilityOverview;
import com.eveassist.industry.internal.application.FacilityRepository;
import com.eveassist.industry.internal.domain.FacilityTerms;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFacilityRepository implements FacilityRepository {

    private final JdbcClient jdbc;

    JdbcFacilityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<FacilityOverview> list(Viewer viewer) {
        return jdbc.sql("""
                SELECT public_id, name, structure_material_percent, rig_material_percent, structure_time_percent, rig_time_percent,
                    structure_cost_percent, system_cost_index_percent, tax_percent, scc_surcharge_percent, updated_at
                FROM facility WHERE owner_account_id = :owner ORDER BY name, public_id
                """)
                .param("owner", viewer.accountId().value())
                .query((rs, n) -> new FacilityOverview(new FacilityId(rs.getObject("public_id", UUID.class)), rs.getString("name"),
                        new FacilityTerms(rs.getBigDecimal("structure_material_percent"), rs.getBigDecimal("rig_material_percent"),
                                rs.getBigDecimal("structure_time_percent"), rs.getBigDecimal("rig_time_percent"),
                                rs.getBigDecimal("structure_cost_percent"), rs.getBigDecimal("system_cost_index_percent"),
                                rs.getBigDecimal("tax_percent"), rs.getBigDecimal("scc_surcharge_percent")),
                        rs.getTimestamp("updated_at").toInstant()))
                .list();
    }
}
