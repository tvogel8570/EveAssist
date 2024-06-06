package com.eveassist.api.sde.pi.impl;

import com.eveassist.api.sde.pi.PiDao;
import com.eveassist.api.sde.pi.entity.PiPinDto;
import com.eveassist.api.sde.pi.entity.PiViewDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PiDaoImpl implements PiDao {
    private final NamedParameterJdbcTemplate template;

    static final String PI_BASE_SQL = """                    
            SELECT ps."schematicID" AS "schematicId", ps."schematicName", ps."cycleTime", pspm."pinTypeID" AS "factoryTypeId",
                it1."typeName" AS "factoryName", pstm."typeID" AS "productTypeId", it2."typeName" AS "productName",
                pstm."isInput", pstm.quantity,
                CASE
                    WHEN it1."typeName"::text ~~ '%Basic%'::text THEN 'Basic'::text
                    WHEN it1."typeName"::text ~~ '%Advanced%'::text THEN 'Advanced'::text
                    WHEN it1."typeName"::text ~~ '%High-Tech%'::text THEN 'High-Tech'::text
                    ELSE 'Unknown'::text
                END AS "factoryType"
            FROM evesde."planetSchematics" ps
                JOIN evesde."planetSchematicsPinMap" pspm ON ps."schematicID" = pspm."schematicID"
                JOIN evesde."invTypes" it1 ON it1."typeID" = pspm."pinTypeID"
                JOIN evesde."planetSchematicsTypeMap" pstm ON ps."schematicID" = pstm."schematicID"
                JOIN evesde."invTypes" it2 ON it2."typeID" = pstm."typeID"
            """;

    @Override
    public List<PiViewDto> piDetailsBySchematicId(Integer schematicId) {
        String sql = PI_BASE_SQL + " where ps.\"schematicID\" = :schematicId";
        SqlParameterSource namedParameters = new MapSqlParameterSource().addValue("schematicId", schematicId);
        return template.query(sql, namedParameters, BeanPropertyRowMapper.newInstance(PiViewDto.class));
    }

    @Override
    public List<PiViewDto> piDetailsBySchematicIdAndFactoryId(Integer schematicId, Integer factoryId) {
        String sql = PI_BASE_SQL + " where ps.\"schematicID\" = :schematicId and pspm.\"pinTypeID\" = :factoryId";
        SqlParameterSource namedParameters = new MapSqlParameterSource()
                .addValue("schematicId", schematicId)
                .addValue("factoryId", factoryId);
        return template.query(sql, namedParameters, BeanPropertyRowMapper.newInstance(PiViewDto.class));
    }

    @Override
    public PiPinDto piPinByType(Integer pinType) {
        String sql = """
                select it."typeID", it."typeName", it.capacity, it."graphicID", ig."groupName"
                    from evesde."invTypes" iT
                        inner join evesde."invGroups" iG on iT."groupID" = iG."groupID"
                    where "typeID" = :pinType""";
        SqlParameterSource namedParameters = new MapSqlParameterSource().addValue("pinType", pinType);
        return template.queryForObject(sql, namedParameters, BeanPropertyRowMapper.newInstance(PiPinDto.class));
    }

    @Override
    public String typeNameByTypeId(Integer typeId) {
        String sql = """
                select it."typeName"
                from evesde."invTypes" iT
                where "typeID" = :typeId""";
        SqlParameterSource namedParameters = new MapSqlParameterSource().addValue("typeId", typeId);
        return template.queryForObject(sql, namedParameters, String.class);
    }

    @Override
    public String celestialNameById(Integer id) {
        String sql = """
                select "itemName"
                from evesde."mapDenormalize"
                where "itemID" = :id""";
        SqlParameterSource namedParameters = new MapSqlParameterSource().addValue("id", id);
        return template.queryForObject(sql, namedParameters, String.class);
    }
}
