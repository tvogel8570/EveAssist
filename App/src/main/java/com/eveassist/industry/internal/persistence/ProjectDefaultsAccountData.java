package com.eveassist.industry.internal.persistence;

import com.eveassist.identity.AccountDataSource;
import com.eveassist.identity.AccountId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The account's project defaults, with the skill levels, for the export. */
@Component
class ProjectDefaultsAccountData implements AccountDataSource {

    private final JdbcClient jdbc;

    ProjectDefaultsAccountData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, List<Map<String, Object>>> sections(AccountId account) {
        var rows = jdbc.sql("""
                SELECT d.price_side, d.isk_per_hour, d.isk_per_cubic_meter, d.contract_fee, d.quote_mode, d.margin_percent, d.premium_percent,
                    d.quote_step, d.split_strategy, d.max_job_seconds, f.name AS facility, d.industry_level, d.advanced_industry_level,
                    d.implant_time_percent
                FROM project_default d LEFT JOIN facility f ON f.id = d.default_facility_id
                WHERE d.owner_account_id = :owner
                """)
                .param("owner", account.value())
                .query((rs, row) -> {
                    var line = new LinkedHashMap<String, Object>();
                    line.put("priceBasis", rs.getString("price_side"));
                    line.put("iskPerHour", rs.getBigDecimal("isk_per_hour"));
                    line.put("iskPerCubicMeter", rs.getBigDecimal("isk_per_cubic_meter"));
                    line.put("contractFee", rs.getBigDecimal("contract_fee"));
                    line.put("quoteMode", rs.getString("quote_mode"));
                    line.put("marginPercent", rs.getBigDecimal("margin_percent"));
                    line.put("premiumPercent", rs.getBigDecimal("premium_percent"));
                    line.put("quoteStep", rs.getBigDecimal("quote_step"));
                    line.put("splitStrategy", rs.getString("split_strategy"));
                    line.put("maxJobSeconds", rs.getObject("max_job_seconds"));
                    line.put("defaultFacility", rs.getString("facility"));
                    line.put("industry", rs.getInt("industry_level"));
                    line.put("advancedIndustry", rs.getInt("advanced_industry_level"));
                    line.put("implantTimePercent", rs.getBigDecimal("implant_time_percent"));
                    line.put("skills", skills(account));
                    return (Map<String, Object>) line;
                }).list();
        return Map.of("projectDefaults", rows);
    }

    private List<Map<String, Object>> skills(AccountId account) {
        return jdbc.sql("""
                SELECT s.skill_type_id, t."typeName" AS name, s.level
                FROM project_default_skill s LEFT JOIN sde."invTypes" t ON t."typeID" = s.skill_type_id
                WHERE s.owner_account_id = :owner ORDER BY s.skill_type_id
                """)
                .param("owner", account.value())
                .query((rs, row) -> {
                    var line = new LinkedHashMap<String, Object>();
                    line.put("typeId", rs.getInt("skill_type_id"));
                    line.put("name", rs.getString("name"));
                    line.put("level", rs.getInt("level"));
                    return (Map<String, Object>) line;
                }).list();
    }
}
