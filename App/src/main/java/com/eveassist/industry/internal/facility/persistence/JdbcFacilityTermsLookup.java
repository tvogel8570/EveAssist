package com.eveassist.industry.internal.facility.persistence;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.FacilityId;
import com.eveassist.industry.internal.facility.application.FacilityTermsLookup;
import com.eveassist.industry.internal.domain.FacilityTerms;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFacilityTermsLookup implements FacilityTermsLookup {

    private final JdbcClient jdbc;

    JdbcFacilityTermsLookup(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<FacilityTerms> termsOf(Viewer viewer, FacilityId id) {
        return jdbc.sql("""
                SELECT structure_material_percent, rig_material_percent, structure_time_percent, rig_time_percent,
                    structure_cost_percent, system_cost_index_percent, tax_percent, scc_surcharge_percent
                FROM facility WHERE public_id = :id AND owner_account_id = :owner
                """)
                .param("id", id.value())
                .param("owner", viewer.accountId().value())
                .query((rs, n) -> new FacilityTerms(
                        rs.getBigDecimal("structure_material_percent"), rs.getBigDecimal("rig_material_percent"),
                        rs.getBigDecimal("structure_time_percent"), rs.getBigDecimal("rig_time_percent"),
                        rs.getBigDecimal("structure_cost_percent"), rs.getBigDecimal("system_cost_index_percent"),
                        rs.getBigDecimal("tax_percent"), rs.getBigDecimal("scc_surcharge_percent")))
                .optional();
    }
}
