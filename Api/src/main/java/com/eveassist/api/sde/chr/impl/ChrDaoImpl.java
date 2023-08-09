package com.eveassist.api.sde.chr.impl;

import com.eveassist.api.sde.chr.ChrDao;
import com.eveassist.api.sde.chr.entity.*;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class ChrDaoImpl implements ChrDao {

    private final NamedParameterJdbcTemplate template;

    public ChrDaoImpl(NamedParameterJdbcTemplate template) {
        this.template = template;
    }

    @Override
    public ChrAncestry getAncestry(Long id) {
        SqlParameterSource namedParameters = new MapSqlParameterSource().addValue("id", id);
        return template.queryForObject(
                "select * from evesde.\"chrAncestries\" where \"ancestryID\" = :id", namedParameters,
                BeanPropertyRowMapper.newInstance(ChrAncestry.class));
    }

    @Override
    public ChrAttribute getAttribute(Long id) {
        SqlParameterSource namedParameters = new MapSqlParameterSource().addValue("id", id);
        return template.queryForObject(
                "select * from evesde.\"chrAttributes\" where \"attributeID\" = :id", namedParameters,
                BeanPropertyRowMapper.newInstance(ChrAttribute.class));
    }

    @Override
    public ChrBloodline getBloodline(Long id) {
        SqlParameterSource namedParameters = new MapSqlParameterSource().addValue("id", id);
        return template.queryForObject(
                "select * from evesde.\"chrBloodlines\" where \"bloodlineID\" = :id", namedParameters,
                BeanPropertyRowMapper.newInstance(ChrBloodline.class));
    }

    @Override
    public ChrFaction getFaction(Long id) {
        SqlParameterSource namedParameters = new MapSqlParameterSource().addValue("id", id);
        return template.queryForObject(
                "select * from evesde.\"chrFactions\" where \"factionID\" = :id", namedParameters,
                BeanPropertyRowMapper.newInstance(ChrFaction.class));
    }

    @Override
    public ChrRace getRace(Long id) {
        SqlParameterSource namedParameters = new MapSqlParameterSource().addValue("id", id);
        return template.queryForObject(
                "select * from evesde.\"chrRaces\" where \"raceID\" = :id", namedParameters,
                BeanPropertyRowMapper.newInstance(ChrRace.class));
    }
}
