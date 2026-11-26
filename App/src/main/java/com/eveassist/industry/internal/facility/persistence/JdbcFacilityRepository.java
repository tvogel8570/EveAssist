package com.eveassist.industry.internal.facility.persistence;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.FacilityId;
import com.eveassist.industry.internal.facility.application.FacilityOverview;
import com.eveassist.industry.internal.facility.application.FacilityRepository;
import com.eveassist.industry.internal.facility.application.FacilityValues;
import com.eveassist.industry.internal.domain.FacilityTerms;
import com.eveassist.industry.internal.facility.domain.StructureType;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFacilityRepository implements FacilityRepository {

    private static final String COLUMNS = """
            public_id, name, structure_type, structure_material_percent, rig_material_percent, structure_time_percent, rig_time_percent,
            structure_cost_percent, system_cost_index_percent, tax_percent, scc_surcharge_percent, updated_at, index_entered_at
            """;

    private final JdbcClient jdbc;

    JdbcFacilityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<FacilityOverview> list(Viewer viewer) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM facility WHERE owner_account_id = :owner ORDER BY name, public_id")
                .param("owner", viewer.accountId().value())
                .query((rs, n) -> overview(rs))
                .list();
    }

    @Override
    public Optional<FacilityOverview> find(Viewer viewer, FacilityId id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM facility WHERE owner_account_id = :owner AND public_id = :id")
                .param("owner", viewer.accountId().value()).param("id", id.value())
                .query((rs, n) -> overview(rs))
                .optional();
    }

    private static FacilityOverview overview(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FacilityOverview(new FacilityId(rs.getObject("public_id", UUID.class)), rs.getString("name"),
                StructureType.ofKey(rs.getString("structure_type")).orElse(null),
                new FacilityTerms(rs.getBigDecimal("structure_material_percent"), rs.getBigDecimal("rig_material_percent"),
                        rs.getBigDecimal("structure_time_percent"), rs.getBigDecimal("rig_time_percent"),
                        rs.getBigDecimal("structure_cost_percent"), rs.getBigDecimal("system_cost_index_percent"),
                        rs.getBigDecimal("tax_percent"), rs.getBigDecimal("scc_surcharge_percent")),
                rs.getTimestamp("updated_at").toInstant(), rs.getTimestamp("index_entered_at").toInstant());
    }

    @Override
    public Map<FacilityId, Integer> draftProjectsUsing(Viewer viewer) {
        Map<FacilityId, Integer> usage = new HashMap<>();
        jdbc.sql("""
                SELECT f.public_id, (
                    SELECT count(*) FROM project p
                    WHERE p.owner_account_id = f.owner_account_id AND p.status = 'draft'
                      AND (p.default_facility_id = f.id
                           OR EXISTS (SELECT 1 FROM project_item i WHERE i.project_id = p.id AND i.facility_id = f.id))) AS drafts
                FROM facility f WHERE f.owner_account_id = :owner
                """)
                .param("owner", viewer.accountId().value())
                .query((rs, n) -> usage.put(new FacilityId(rs.getObject("public_id", UUID.class)), rs.getInt("drafts")))
                .list();
        return usage;
    }

    @Override
    public FacilityId insert(Viewer viewer, FacilityValues values) {
        var id = UUID.randomUUID();
        var terms = values.terms();
        jdbc.sql("""
                INSERT INTO facility (public_id, owner_account_id, name, structure_type, structure_material_percent, rig_material_percent,
                    structure_time_percent, rig_time_percent, structure_cost_percent, system_cost_index_percent, tax_percent, scc_surcharge_percent)
                VALUES (:id, :owner, :name, :type, :sm, :rm, :st, :rt, :sc, :index, :tax, :scc)
                """)
                .param("id", id).param("owner", viewer.accountId().value()).param("name", values.name())
                .param("type", values.structureType() == null ? null : values.structureType().key(), Types.VARCHAR)
                .param("sm", terms.structureMaterialPercent()).param("rm", terms.rigMaterialPercent())
                .param("st", terms.structureTimePercent()).param("rt", terms.rigTimePercent())
                .param("sc", terms.structureCostPercent()).param("index", terms.systemCostIndexPercent())
                .param("tax", terms.taxPercent()).param("scc", terms.sccSurchargePercent())
                .update();
        return new FacilityId(id);
    }

    @Override
    public boolean update(Viewer viewer, FacilityId id, FacilityValues values) {
        var terms = values.terms();
        // every right-hand side sees the row as it was, so a time moves only when what it is about changed
        return jdbc.sql("""
                UPDATE facility SET
                    updated_at = CASE WHEN (name, structure_type, structure_material_percent, rig_material_percent, structure_time_percent,
                            rig_time_percent, structure_cost_percent, system_cost_index_percent, tax_percent, scc_surcharge_percent)
                        IS DISTINCT FROM (CAST(:name AS text), CAST(:type AS text), CAST(:sm AS numeric), CAST(:rm AS numeric), CAST(:st AS numeric),
                            CAST(:rt AS numeric), CAST(:sc AS numeric), CAST(:index AS numeric), CAST(:tax AS numeric), CAST(:scc AS numeric))
                        THEN now() ELSE updated_at END,
                    index_entered_at = CASE WHEN system_cost_index_percent IS DISTINCT FROM CAST(:index AS numeric) THEN now() ELSE index_entered_at END,
                    name = :name, structure_type = CAST(:type AS text), structure_material_percent = :sm, rig_material_percent = :rm,
                    structure_time_percent = :st, rig_time_percent = :rt, structure_cost_percent = :sc, system_cost_index_percent = :index,
                    tax_percent = :tax, scc_surcharge_percent = :scc
                WHERE public_id = :id AND owner_account_id = :owner
                """)
                .param("id", id.value()).param("owner", viewer.accountId().value()).param("name", values.name())
                .param("type", values.structureType() == null ? null : values.structureType().key(), Types.VARCHAR)
                .param("sm", terms.structureMaterialPercent()).param("rm", terms.rigMaterialPercent())
                .param("st", terms.structureTimePercent()).param("rt", terms.rigTimePercent())
                .param("sc", terms.structureCostPercent()).param("index", terms.systemCostIndexPercent())
                .param("tax", terms.taxPercent()).param("scc", terms.sccSurchargePercent())
                .update() > 0;
    }

    @Override
    public Optional<Integer> delete(Viewer viewer, FacilityId id) {
        // the version first: the foreign keys clear the references when the facility goes, and then there is nothing left to find the projects by
        var bumped = jdbc.sql("""
                UPDATE project SET version = version + 1
                WHERE owner_account_id = :owner AND status = 'draft'
                  AND (default_facility_id = (SELECT id FROM facility WHERE public_id = :id AND owner_account_id = :owner)
                       OR id IN (SELECT i.project_id FROM project_item i
                                 WHERE i.facility_id = (SELECT id FROM facility WHERE public_id = :id AND owner_account_id = :owner)))
                """)
                .param("owner", viewer.accountId().value()).param("id", id.value()).update();
        var deleted = jdbc.sql("DELETE FROM facility WHERE public_id = :id AND owner_account_id = :owner")
                .param("owner", viewer.accountId().value()).param("id", id.value()).update();
        return deleted > 0 ? Optional.of(bumped) : Optional.empty();
    }
}
