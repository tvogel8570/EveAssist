package com.eveassist.industry.internal.persistence;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.FacilityId;
import com.eveassist.industry.PlanId;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.application.FacilityNotFoundException;
import com.eveassist.industry.internal.application.ProjectRepository;
import com.eveassist.industry.internal.domain.BlueprintSource;
import com.eveassist.industry.internal.domain.PlanPrice;
import com.eveassist.industry.internal.domain.PlanProduct;
import com.eveassist.industry.internal.domain.PriceSide;
import com.eveassist.industry.internal.domain.Project;
import com.eveassist.industry.internal.domain.ProjectBuilder;
import com.eveassist.industry.internal.domain.ProjectConfig;
import com.eveassist.industry.internal.domain.ProjectItem;
import com.eveassist.industry.internal.domain.ProjectSettings;
import com.eveassist.industry.internal.domain.ProjectSettings.QuoteMode;
import com.eveassist.industry.internal.domain.ProjectStatus;
import com.eveassist.industry.internal.domain.SplitPolicy;
import com.eveassist.industry.internal.domain.SplitStrategy;
import com.eveassist.sde.TypeId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The project's Draft in its tables. Enumerations are stored in lower case, as the checks of the tables spell them. A saved project
 * replaces what was stored, as a plan does, and the version check decides whether the save may happen at all.
 */
@Repository
class JdbcProjectRepository implements ProjectRepository {

    private final JdbcClient jdbc;

    JdbcProjectRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ProjectId create(Viewer viewer, String name, PlanId sourcePlan) {
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO project (public_id, owner_account_id, name, source_plan_id)
                VALUES (:id, :owner, :name, (SELECT id FROM plan WHERE public_id = :plan AND owner_account_id = :owner))
                """)
                .param("id", id)
                .param("owner", viewer.accountId().value())
                .param("name", name)
                .param("plan", sourcePlan == null ? null : sourcePlan.value(), Types.OTHER)
                .update();
        return new ProjectId(id);
    }

    @Override
    public Optional<Project> find(Viewer viewer, ProjectId id) {
        record Row(long key, String name, long version, ProjectStatus status, int lastQuoteNumber, boolean archived,
                   ProjectConfig config) {
        }
        var row = jdbc.sql("""
                SELECT p.id, p.name, p.version, p.status, p.last_quote_number, p.archived_at IS NOT NULL AS archived,
                    p.isk_per_hour, p.isk_per_cubic_meter, p.contract_fee, p.quote_mode, p.margin_percent, p.premium_percent,
                    p.quote_step, p.price_side, p.split_strategy, p.max_job_seconds, f.public_id AS default_facility,
                    p.buyer_name, p.note, p.industry_level, p.advanced_industry_level, p.implant_time_percent
                FROM project p LEFT JOIN facility f ON f.id = p.default_facility_id
                WHERE p.public_id = :id AND p.owner_account_id = :owner
                """)
                .param("id", id.value())
                .param("owner", viewer.accountId().value())
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getString("name"), rs.getLong("version"),
                        enumOf(ProjectStatus.class, rs.getString("status")), rs.getInt("last_quote_number"),
                        rs.getBoolean("archived"), config(rs)))
                .optional();
        return row.map(project -> new Project(id, project.name(), project.version(), project.status(), project.lastQuoteNumber(),
                project.archived(), project.config(), products(project.key()), boughtParts(project.key()), items(project.key()),
                prices(project.key())));
    }

    private static ProjectConfig config(ResultSet rs) throws SQLException {
        var settings = new ProjectSettings(rs.getBigDecimal("isk_per_hour"), rs.getBigDecimal("isk_per_cubic_meter"),
                rs.getBigDecimal("contract_fee"), enumOf(QuoteMode.class, rs.getString("quote_mode")),
                rs.getBigDecimal("margin_percent"), rs.getBigDecimal("premium_percent"), rs.getBigDecimal("quote_step"));
        var policy = new SplitPolicy(enumOf(SplitStrategy.class, rs.getString("split_strategy")), (Long) rs.getObject("max_job_seconds"));
        var facility = rs.getObject("default_facility", UUID.class);
        var builder = new ProjectBuilder(rs.getInt("industry_level"), rs.getInt("advanced_industry_level"),
                rs.getBigDecimal("implant_time_percent"));
        return new ProjectConfig(settings, enumOf(PriceSide.class, rs.getString("price_side")), policy,
                facility == null ? null : new FacilityId(facility), builder, rs.getString("buyer_name"), rs.getString("note"));
    }

    private List<PlanProduct> products(long projectKey) {
        return jdbc.sql("SELECT type_id, quantity FROM project_product WHERE project_id = :p ORDER BY type_id")
                .param("p", projectKey)
                .query((rs, n) -> new PlanProduct(new TypeId(rs.getInt("type_id")), rs.getLong("quantity")))
                .list();
    }

    private Set<TypeId> boughtParts(long projectKey) {
        return Set.copyOf(jdbc.sql("SELECT type_id FROM project_bought_part WHERE project_id = :p")
                .param("p", projectKey)
                .query((rs, n) -> new TypeId(rs.getInt("type_id")))
                .list());
    }

    private Map<TypeId, PlanPrice> prices(long projectKey) {
        var prices = new HashMap<TypeId, PlanPrice>();
        jdbc.sql("SELECT type_id, sell, buy, as_of FROM project_price WHERE project_id = :p")
                .param("p", projectKey)
                .query((rs, n) -> prices.put(new TypeId(rs.getInt("type_id")), new PlanPrice(
                        rs.getBigDecimal("sell"), rs.getBigDecimal("buy"), rs.getTimestamp("as_of").toInstant())))
                .list();
        return prices;
    }

    private Map<TypeId, ProjectItem> items(long projectKey) {
        var skills = new HashMap<Integer, Map<TypeId, Integer>>();
        jdbc.sql("SELECT item_type_id, skill_type_id, level FROM project_item_skill WHERE project_id = :p")
                .param("p", projectKey)
                .query((rs, n) -> skills.computeIfAbsent(rs.getInt("item_type_id"), key -> new HashMap<>())
                        .put(new TypeId(rs.getInt("skill_type_id")), rs.getInt("level")))
                .list();
        var items = new HashMap<TypeId, ProjectItem>();
        jdbc.sql("""
                SELECT i.type_id, i.blueprint_kind, i.me, i.te, i.blueprint_cost_per_run, f.public_id AS facility,
                    i.split_strategy, i.max_job_seconds
                FROM project_item i LEFT JOIN facility f ON f.id = i.facility_id
                WHERE i.project_id = :p
                """)
                .param("p", projectKey)
                .query((rs, n) -> {
                    var type = new TypeId(rs.getInt("type_id"));
                    var facility = rs.getObject("facility", UUID.class);
                    var strategy = rs.getString("split_strategy");
                    var policy = strategy == null ? null
                            : new SplitPolicy(enumOf(SplitStrategy.class, strategy), (Long) rs.getObject("max_job_seconds"));
                    return items.put(type, new ProjectItem(type, enumOf(BlueprintSource.class, rs.getString("blueprint_kind")),
                            rs.getInt("me"), rs.getInt("te"), rs.getBigDecimal("blueprint_cost_per_run"),
                            facility == null ? null : new FacilityId(facility), policy,
                            skills.getOrDefault(type.value(), Map.of())));
                })
                .list();
        return items;
    }

    @Override
    public Optional<Long> save(Viewer viewer, Project project) {
        checkFacilities(viewer, project);
        var config = project.config();
        var settings = config.settings();
        var owner = viewer.accountId().value();
        var updated = jdbc.sql("""
                UPDATE project SET version = version + 1, status = :status,
                    isk_per_hour = :perHour, isk_per_cubic_meter = :perM3, contract_fee = :fee, quote_mode = :mode,
                    margin_percent = :margin, premium_percent = :premium, quote_step = :step, price_side = :side,
                    split_strategy = :strategy, max_job_seconds = :maxJob,
                    default_facility_id = (SELECT id FROM facility WHERE public_id = :facility AND owner_account_id = :owner),
                    buyer_name = :buyer, note = :note, industry_level = :industry, advanced_industry_level = :advanced,
                    implant_time_percent = :implant
                WHERE public_id = :id AND owner_account_id = :owner AND version = :version
                RETURNING id, version
                """)
                .param("status", lower(project.status()))
                .param("perHour", settings.iskPerHour())
                .param("perM3", settings.iskPerCubicMeter())
                .param("fee", settings.contractFee())
                .param("mode", lower(settings.mode()))
                .param("margin", settings.marginPercent())
                .param("premium", settings.premiumPercent())
                .param("step", settings.quoteStep())
                .param("side", lower(config.priceSide()))
                .param("strategy", lower(config.defaultPolicy().strategy()))
                .param("maxJob", config.defaultPolicy().maxJobSeconds(), Types.BIGINT)
                .param("facility", config.defaultFacility() == null ? null : config.defaultFacility().value(), Types.OTHER)
                .param("buyer", config.buyerName(), Types.VARCHAR)
                .param("note", config.note(), Types.VARCHAR)
                .param("industry", config.builder().industry())
                .param("advanced", config.builder().advancedIndustry())
                .param("implant", config.builder().implantTimePercent())
                .param("id", project.id().value())
                .param("owner", owner)
                .param("version", project.version())
                .query((rs, n) -> new long[] {rs.getLong("id"), rs.getLong("version")})
                .optional();
        updated.ifPresent(row -> {
            saveProducts(row[0], project.products());
            saveBoughtParts(row[0], project.boughtParts());
            saveItems(row[0], owner, project.items());
            savePrices(row[0], project.prices());
        });
        return updated.map(row -> row[1]);
    }

    /** A foreign key cannot say that a facility belongs to the same account, so it is checked here. */
    private void checkFacilities(Viewer viewer, Project project) {
        Set<UUID> referenced = new HashSet<>();
        if (project.config().defaultFacility() != null) {
            referenced.add(project.config().defaultFacility().value());
        }
        project.items().values().stream().filter(item -> item.facility() != null)
                .forEach(item -> referenced.add(item.facility().value()));
        if (referenced.isEmpty()) {
            return;
        }
        var found = jdbc.sql("SELECT count(*) FROM facility WHERE owner_account_id = :owner AND public_id IN (:ids)")
                .param("owner", viewer.accountId().value())
                .param("ids", List.copyOf(referenced))
                .query(Integer.class)
                .single();
        if (found != referenced.size()) {
            throw new FacilityNotFoundException();
        }
    }

    private void saveProducts(long projectKey, List<PlanProduct> products) {
        for (var product : products) {
            jdbc.sql("""
                    INSERT INTO project_product (project_id, type_id, quantity) VALUES (:p, :type, :quantity)
                    ON CONFLICT (project_id, type_id) DO UPDATE SET quantity = excluded.quantity
                    """)
                    .param("p", projectKey)
                    .param("type", product.type().value())
                    .param("quantity", product.quantity())
                    .update();
        }
        deleteOthers("project_product", "type_id", projectKey, products.stream().map(product -> product.type().value()).toList());
    }

    private void saveBoughtParts(long projectKey, Set<TypeId> parts) {
        jdbc.sql("DELETE FROM project_bought_part WHERE project_id = :p").param("p", projectKey).update();
        for (var part : parts) {
            jdbc.sql("INSERT INTO project_bought_part (project_id, type_id) VALUES (:p, :type)")
                    .param("p", projectKey)
                    .param("type", part.value())
                    .update();
        }
    }

    private void saveItems(long projectKey, UUID owner, Map<TypeId, ProjectItem> items) {
        // the skills hang on their item, so they go first and come back after the items are in place
        jdbc.sql("DELETE FROM project_item_skill WHERE project_id = :p").param("p", projectKey).update();
        for (var item : items.values()) {
            var policy = item.policy();
            jdbc.sql("""
                    INSERT INTO project_item (project_id, type_id, blueprint_kind, me, te, blueprint_cost_per_run, facility_id,
                                              split_strategy, max_job_seconds)
                    VALUES (:p, :type, :kind, :me, :te, :cost,
                            (SELECT id FROM facility WHERE public_id = :facility AND owner_account_id = :owner), :strategy, :maxJob)
                    ON CONFLICT (project_id, type_id) DO UPDATE SET blueprint_kind = excluded.blueprint_kind, me = excluded.me,
                        te = excluded.te, blueprint_cost_per_run = excluded.blueprint_cost_per_run,
                        facility_id = excluded.facility_id, split_strategy = excluded.split_strategy,
                        max_job_seconds = excluded.max_job_seconds
                    """)
                    .param("p", projectKey)
                    .param("type", item.type().value())
                    .param("kind", lower(item.source()))
                    .param("me", item.me())
                    .param("te", item.te())
                    .param("cost", item.blueprintCostPerRun())
                    .param("facility", item.facility() == null ? null : item.facility().value(), Types.OTHER)
                    .param("owner", owner)
                    .param("strategy", policy == null ? null : lower(policy.strategy()), Types.VARCHAR)
                    .param("maxJob", policy == null ? null : policy.maxJobSeconds(), Types.BIGINT)
                    .update();
        }
        deleteOthers("project_item", "type_id", projectKey, items.keySet().stream().map(TypeId::value).toList());
        for (var item : items.values()) {
            for (var skill : item.skillLevels().entrySet()) {
                jdbc.sql("""
                        INSERT INTO project_item_skill (project_id, item_type_id, skill_type_id, level)
                        VALUES (:p, :item, :skill, :level)
                        """)
                        .param("p", projectKey)
                        .param("item", item.type().value())
                        .param("skill", skill.getKey().value())
                        .param("level", skill.getValue())
                        .update();
            }
        }
    }

    private void savePrices(long projectKey, Map<TypeId, PlanPrice> prices) {
        for (var entry : prices.entrySet()) {
            jdbc.sql("""
                    INSERT INTO project_price (project_id, type_id, sell, buy, as_of) VALUES (:p, :type, :sell, :buy, :asOf)
                    ON CONFLICT (project_id, type_id) DO UPDATE SET sell = excluded.sell, buy = excluded.buy,
                        as_of = excluded.as_of
                    WHERE (project_price.sell, project_price.buy, project_price.as_of)
                        IS DISTINCT FROM (excluded.sell, excluded.buy, excluded.as_of)
                    """)
                    .param("p", projectKey)
                    .param("type", entry.getKey().value())
                    .param("sell", entry.getValue().sell())
                    .param("buy", entry.getValue().buy())
                    .param("asOf", Timestamp.from(entry.getValue().asOf()))
                    .update();
        }
        deleteOthers("project_price", "type_id", projectKey, prices.keySet().stream().map(TypeId::value).toList());
    }

    /** Removes the rows of the project whose type is not among those kept. */
    private void deleteOthers(String table, String column, long projectKey, List<Integer> keep) {
        if (keep.isEmpty()) {
            jdbc.sql("DELETE FROM " + table + " WHERE project_id = :p").param("p", projectKey).update();
        } else {
            jdbc.sql("DELETE FROM " + table + " WHERE project_id = :p AND " + column + " NOT IN (:keep)")
                    .param("p", projectKey)
                    .param("keep", keep)
                    .update();
        }
    }

    @Override
    public boolean rename(Viewer viewer, ProjectId id, String name) {
        return jdbc.sql("UPDATE project SET name = :name WHERE public_id = :id AND owner_account_id = :owner")
                .param("name", name)
                .param("id", id.value())
                .param("owner", viewer.accountId().value())
                .update() > 0;
    }

    @Override
    public boolean setArchived(Viewer viewer, ProjectId id, boolean archived) {
        return jdbc.sql("""
                UPDATE project SET archived_at = CASE WHEN :archived THEN coalesce(archived_at, now()) ELSE NULL END
                WHERE public_id = :id AND owner_account_id = :owner
                """)
                .param("archived", archived)
                .param("id", id.value())
                .param("owner", viewer.accountId().value())
                .update() > 0;
    }

    @Override
    public boolean delete(Viewer viewer, ProjectId id) {
        return jdbc.sql("DELETE FROM project WHERE public_id = :id AND owner_account_id = :owner")
                .param("id", id.value())
                .param("owner", viewer.accountId().value())
                .update() > 0;
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String stored) {
        return Enum.valueOf(type, stored.toUpperCase(Locale.ROOT));
    }
}
