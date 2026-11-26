package com.eveassist.industry.internal.persistence;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.internal.facility.application.FacilityNotFoundException;
import com.eveassist.industry.internal.application.ProjectDefaultsRepository;
import com.eveassist.industry.internal.domain.ProjectConfig;
import com.eveassist.sde.TypeId;
import java.sql.Types;
import java.util.HashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** The columns are those of {@code project}, so a row is read by the same mapping as a project's configuration. */
@Repository
class JdbcProjectDefaultsRepository implements ProjectDefaultsRepository {

    private final JdbcClient jdbc;

    JdbcProjectDefaultsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ProjectConfig> find(Viewer viewer) {
        var owner = viewer.accountId().value();
        return jdbc.sql("""
                SELECT d.isk_per_hour, d.isk_per_cubic_meter, d.contract_fee, d.quote_mode, d.margin_percent, d.premium_percent,
                    d.quote_step, d.price_side, d.split_strategy, d.max_job_seconds, f.public_id AS default_facility,
                    NULL AS buyer_name, NULL AS note, d.industry_level, d.advanced_industry_level, d.implant_time_percent
                FROM project_default d LEFT JOIN facility f ON f.id = d.default_facility_id
                WHERE d.owner_account_id = :owner
                """)
                .param("owner", owner)
                .query((rs, n) -> JdbcProjectRepository.config(rs))
                .optional()
                .map(config -> JdbcProjectRepository.withSkills(config, skills(owner)));
    }

    private HashMap<TypeId, Integer> skills(UUID owner) {
        var skills = new HashMap<TypeId, Integer>();
        jdbc.sql("SELECT skill_type_id, level FROM project_default_skill WHERE owner_account_id = :owner")
                .param("owner", owner)
                .query((rs, n) -> skills.put(new TypeId(rs.getInt("skill_type_id")), rs.getInt("level")))
                .list();
        return skills;
    }

    @Override
    @Transactional
    public void save(Viewer viewer, ProjectConfig defaults) {
        var owner = viewer.accountId().value();
        var facility = defaults.defaultFacility() == null ? null : defaults.defaultFacility().value();
        if (facility != null && jdbc.sql("SELECT count(*) FROM facility WHERE public_id = :id AND owner_account_id = :owner")
                .param("id", facility).param("owner", owner).query(Integer.class).single() == 0) {
            throw new FacilityNotFoundException();
        }
        var settings = defaults.settings();
        var policy = defaults.defaultPolicy();
        var builder = defaults.builder();
        jdbc.sql("""
                INSERT INTO project_default (owner_account_id, isk_per_hour, isk_per_cubic_meter, contract_fee, quote_mode, margin_percent,
                    premium_percent, quote_step, price_side, split_strategy, max_job_seconds, default_facility_id, industry_level,
                    advanced_industry_level, implant_time_percent)
                VALUES (:owner, :perHour, :perM3, :fee, :mode, :margin, :premium, :step, :side, :strategy, :maxJob,
                    (SELECT id FROM facility WHERE public_id = :facility AND owner_account_id = :owner), :industry, :advanced, :implant)
                ON CONFLICT (owner_account_id) DO UPDATE SET isk_per_hour = excluded.isk_per_hour,
                    isk_per_cubic_meter = excluded.isk_per_cubic_meter, contract_fee = excluded.contract_fee, quote_mode = excluded.quote_mode,
                    margin_percent = excluded.margin_percent, premium_percent = excluded.premium_percent, quote_step = excluded.quote_step,
                    price_side = excluded.price_side, split_strategy = excluded.split_strategy, max_job_seconds = excluded.max_job_seconds,
                    default_facility_id = excluded.default_facility_id, industry_level = excluded.industry_level,
                    advanced_industry_level = excluded.advanced_industry_level, implant_time_percent = excluded.implant_time_percent
                """)
                .param("owner", owner)
                .param("perHour", settings.iskPerHour())
                .param("perM3", settings.iskPerCubicMeter())
                .param("fee", settings.contractFee())
                .param("mode", lower(settings.mode()))
                .param("margin", settings.marginPercent())
                .param("premium", settings.premiumPercent())
                .param("step", settings.quoteStep())
                .param("side", lower(defaults.priceSide()))
                .param("strategy", lower(policy.strategy()))
                .param("maxJob", policy.maxJobSeconds(), Types.BIGINT)
                .param("facility", facility, Types.OTHER)
                .param("industry", builder.industry())
                .param("advanced", builder.advancedIndustry())
                .param("implant", builder.implantTimePercent())
                .update();
        jdbc.sql("DELETE FROM project_default_skill WHERE owner_account_id = :owner").param("owner", owner).update();
        for (var skill : builder.skillLevels().entrySet()) {
            jdbc.sql("INSERT INTO project_default_skill (owner_account_id, skill_type_id, level) VALUES (:owner, :skill, :level)")
                    .param("owner", owner)
                    .param("skill", skill.getKey().value())
                    .param("level", skill.getValue())
                    .update();
        }
    }

    @Override
    public void delete(Viewer viewer) {
        // the skills go with the row
        jdbc.sql("DELETE FROM project_default WHERE owner_account_id = :owner").param("owner", viewer.accountId().value()).update();
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
