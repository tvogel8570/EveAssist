package com.eveassist.sde.internal;

import com.eveassist.sde.ItemType;
import com.eveassist.sde.Recipe;
import com.eveassist.sde.RecipeMaterial;
import com.eveassist.sde.TimeSkill;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Loads what costing needs at start-up, so that costing a plan walks memory rather than the database. */
@Component
public class IndustryData {

    private static final int MANUFACTURING = 1;
    /** The dogma attribute `manufactureTimePerLevel` of a skill. */
    private static final int MANUFACTURE_TIME_PER_LEVEL = 1982;

    private final String version;
    private final Map<TypeId, ItemType> types;
    private final Map<TypeId, Recipe> recipes;
    private final Map<TypeId, Recipe> blueprintRecipes;
    private final Set<TypeId> blueprints;
    private final Set<TypeId> abyssalTypes;
    private final Map<TypeId, BigDecimal> volumes;
    private final Map<TypeId, List<TimeSkill>> timeSkills;

    IndustryData(JdbcClient jdbc) {
        version = jdbc.sql("SELECT name FROM sde.sde_version ORDER BY imported_at DESC LIMIT 1")
                .query(String.class)
                .optional()
                .orElseThrow(() -> new IllegalStateException("The SDE has not been loaded"));
        types = jdbc.sql("""
                SELECT t."typeID", t."typeName", g."groupName"
                FROM sde."invTypes" t JOIN sde."invGroups" g ON g."groupID" = t."groupID"
                WHERE t."typeName" IS NOT NULL
                """)
                .query((rs, row) -> new ItemType(
                        new TypeId(rs.getInt("typeID")), rs.getString("typeName"), rs.getString("groupName")))
                .list().stream()
                .collect(Collectors.toUnmodifiableMap(ItemType::id, type -> type));
        recipes = Map.copyOf(loadRecipes(jdbc));
        blueprintRecipes = Map.copyOf(loadBlueprintRecipes(jdbc, recipes));
        volumes = Map.copyOf(loadVolumes(jdbc));
        timeSkills = Map.copyOf(loadTimeSkills(jdbc));
        // What a mutaplasmid turns an item into. Names are no guide: "50MN Abyssal Microwarpdrive" is one, "Abyssal Trace" is not.
        abyssalTypes = Set.copyOf(jdbc.sql("""
                SELECT DISTINCT "resultingTypeID" FROM sde."dynamicItemAttributeMappings"
                """).query((rs, row) -> new TypeId(rs.getInt("resultingTypeID"))).list());
        blueprints = Set.copyOf(jdbc.sql("""
                SELECT DISTINCT p."typeID" FROM sde."industryActivityProducts" p
                JOIN sde."invTypes" t ON t."typeID" = p."typeID" AND t.published
                """).query((rs, row) -> new TypeId(rs.getInt("typeID"))).list());
    }

    private static Map<TypeId, Recipe> loadRecipes(JdbcClient jdbc) {
        record Row(int product, int units, int seconds, int material, long quantity) {
        }
        var rows = jdbc.sql("""
                SELECT p."productTypeID", p.quantity AS units, COALESCE(a.time, 0) AS seconds, m."materialTypeID", m.quantity
                FROM sde."industryActivityProducts" p
                LEFT JOIN sde."industryActivity" a ON a."typeID" = p."typeID" AND a."activityID" = p."activityID"
                JOIN sde."industryActivityMaterials" m ON m."typeID" = p."typeID" AND m."activityID" = p."activityID"
                JOIN sde."invTypes" blueprint ON blueprint."typeID" = p."typeID" AND blueprint.published
                WHERE p."activityID" = :activity
                ORDER BY p."productTypeID", m."materialTypeID"
                """)
                .param("activity", MANUFACTURING)
                .query((rs, row) -> new Row(rs.getInt("productTypeID"), rs.getInt("units"),
                        rs.getInt("seconds"), rs.getInt("materialTypeID"), rs.getLong("quantity")))
                .list();
        var materials = new HashMap<Integer, List<RecipeMaterial>>();
        var units = new HashMap<Integer, Integer>();
        var seconds = new HashMap<Integer, Integer>();
        for (var row : rows) {
            units.put(row.product(), row.units());
            seconds.put(row.product(), row.seconds());
            materials.computeIfAbsent(row.product(), product -> new ArrayList<>())
                    .add(new RecipeMaterial(new TypeId(row.material()), row.quantity()));
        }
        var result = new HashMap<TypeId, Recipe>();
        materials.forEach((product, list) -> result.put(
                new TypeId(product), new Recipe(new TypeId(product), units.get(product), seconds.get(product), list)));
        return result;
    }

    /**
     * The volume of one unit of what manufacturing makes and uses, in cubic meters. Where an item has a packaged volume that is the one
     * (a Vexor is 115,000 m3 assembled and 10,000 packaged, and a ship is hauled packaged). Only types that appear in a recipe are kept.
     */
    private static Map<TypeId, BigDecimal> loadVolumes(JdbcClient jdbc) {
        var volumes = new HashMap<TypeId, BigDecimal>();
        jdbc.sql("""
                SELECT t."typeID", coalesce(t."packagedVolume", t.volume) AS unit_volume FROM sde."invTypes" t
                WHERE coalesce(t."packagedVolume", t.volume) IS NOT NULL AND t."typeID" IN (
                    SELECT "productTypeID" FROM sde."industryActivityProducts" WHERE "activityID" = :activity
                    UNION SELECT "materialTypeID" FROM sde."industryActivityMaterials" WHERE "activityID" = :activity)
                """)
                .param("activity", MANUFACTURING)
                .query((rs, row) -> volumes.put(new TypeId(rs.getInt("typeID")), rs.getBigDecimal("unit_volume")))
                .list();
        return volumes;
    }

    /**
     * For each product, the skills its blueprint requires for manufacturing that save time (attribute 1982). The skill's level
     * that the blueprint requires is not used: the builder's own level counts. The attribute is negative for a saving, and is stored
     * here as a positive percent.
     */
    private static Map<TypeId, List<TimeSkill>> loadTimeSkills(JdbcClient jdbc) {
        var skills = new HashMap<TypeId, List<TimeSkill>>();
        jdbc.sql("""
                SELECT p."productTypeID", s."skillID", abs(coalesce(d."valueFloat", d."valueInt")) AS percent_per_level
                FROM sde."industryActivityProducts" p
                JOIN sde."invTypes" blueprint ON blueprint."typeID" = p."typeID" AND blueprint.published
                JOIN sde."industryActivitySkills" s ON s."typeID" = p."typeID" AND s."activityID" = p."activityID"
                JOIN sde."dgmTypeAttributes" d ON d."typeID" = s."skillID" AND d."attributeID" = :attribute
                WHERE p."activityID" = :activity
                ORDER BY p."productTypeID", s."skillID"
                """)
                .param("activity", MANUFACTURING)
                .param("attribute", MANUFACTURE_TIME_PER_LEVEL)
                .query((rs, row) -> skills.computeIfAbsent(new TypeId(rs.getInt("productTypeID")), key -> new ArrayList<>())
                        .add(new TimeSkill(new TypeId(rs.getInt("skillID")), rs.getBigDecimal("percent_per_level"))))
                .list();
        skills.replaceAll((product, list) -> List.copyOf(list));
        return skills;
    }

    private static Map<TypeId, Recipe> loadBlueprintRecipes(JdbcClient jdbc, Map<TypeId, Recipe> recipes) {
        return jdbc.sql("""
                SELECT p."typeID" AS blueprint_type, p."productTypeID" AS product_type
                FROM sde."industryActivityProducts" p
                JOIN sde."invTypes" blueprint ON blueprint."typeID" = p."typeID" AND blueprint.published
                WHERE p."activityID" = :activity
                ORDER BY p."typeID", p."productTypeID"
                """)
                .param("activity", MANUFACTURING)
                .query((rs, row) -> Map.entry(new TypeId(rs.getInt("blueprint_type")),
                        new TypeId(rs.getInt("product_type"))))
                .list().stream()
                .filter(entry -> recipes.containsKey(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> recipes.get(entry.getValue()), (first, ignored) -> first));
    }

    public String version() {
        return version;
    }

    public Optional<ItemType> itemType(TypeId id) {
        return Optional.ofNullable(types.get(id));
    }

    public Optional<Recipe> recipeFor(TypeId product) {
        return Optional.ofNullable(recipes.get(product));
    }

    /** The manufacturing recipe produced by a blueprint type, when its output is in the SDE. */
    public Optional<Recipe> recipeOfBlueprint(TypeId blueprint) {
        return Optional.ofNullable(blueprintRecipes.get(blueprint));
    }

    /** Names that start with the text come first. */
    public List<ItemType> searchBuildable(String text, int limit) {
        var wanted = text.trim().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) {
            return List.of();
        }
        return recipes.keySet().stream()
                .map(types::get)
                .filter(type -> type != null && type.name().toLowerCase(Locale.ROOT).contains(wanted))
                .sorted(Comparator
                        .comparing((ItemType type) -> !type.name().toLowerCase(Locale.ROOT).startsWith(wanted))
                        .thenComparing(ItemType::name))
                .limit(limit)
                .toList();
    }

    /** Blueprints, found by name. Names that start with the text come first. */
    public List<ItemType> searchBlueprints(String text, int limit) {
        var wanted = text.trim().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) {
            return List.of();
        }
        return blueprints.stream()
                .map(types::get)
                .filter(type -> type != null && type.name().toLowerCase(Locale.ROOT).contains(wanted))
                .sorted(Comparator
                        .comparing((ItemType type) -> !type.name().toLowerCase(Locale.ROOT).startsWith(wanted))
                        .thenComparing(ItemType::name))
                .limit(limit)
                .toList();
    }

    /** True for the types a mutaplasmid produces, whose value depends on the attributes each item was rolled with. */
    public boolean isAbyssal(TypeId id) {
        return abyssalTypes.contains(id);
    }

    public boolean isBlueprint(TypeId id) {
        return blueprints.contains(id);
    }

    /** The volume of one unit, the packaged volume where there is one. Empty for a type no recipe makes or uses. */
    public Optional<BigDecimal> unitVolume(TypeId id) {
        return Optional.ofNullable(volumes.get(id));
    }

    /** The skills the product's blueprint requires that shorten its manufacturing time, with the percent each level saves. */
    public List<TimeSkill> timeSkillsOf(TypeId product) {
        return timeSkills.getOrDefault(product, List.of());
    }

    public Set<TypeId> materialTypes() {
        return recipes.values().stream()
                .flatMap(recipe -> recipe.materials().stream())
                .map(RecipeMaterial::material)
                .collect(Collectors.toUnmodifiableSet());
    }
}
