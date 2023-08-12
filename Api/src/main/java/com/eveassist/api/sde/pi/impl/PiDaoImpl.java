package com.eveassist.api.sde.pi.impl;

import com.eveassist.api.sde.pi.PiDao;
import com.eveassist.api.sde.pi.entity.PiViewDto;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PiDaoImpl implements PiDao {
    private final NamedParameterJdbcTemplate template;

    public PiDaoImpl(NamedParameterJdbcTemplate template) {
        this.template = template;
    }

    @Override
    public PiViewDto piDetailsBySchematicId(Integer schematicId) {
        return null;
    }

    @Override
    public PiViewDto piDetailsBySchematicIdAndFactoryId(Integer schematicId, Integer factoryId) {
        return null;
    }
}
