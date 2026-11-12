package com.eveassist.sde.internal;

import com.eveassist.sde.ItemType;
import com.eveassist.sde.Recipe;
import com.eveassist.sde.RecipeMaterial;
import com.eveassist.sde.TypeId;
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

    private final String version;
    private final Map<TypeId, ItemType> types;
    private final Map<TypeId, Recipe> recipes;
    private final Set<TypeId> blueprints;
    private final Set<TypeId> abyssalTypes;

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

    public String version() {
        return version;
    }

    public Optional<ItemType> itemType(TypeId id) {
        return Optional.ofNullable(types.get(id));
    }

    public Optional<Recipe> recipeFor(TypeId product) {
        return Optional.ofNullable(recipes.get(product));
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

    public Set<TypeId> materialTypes() {
        return recipes.values().stream()
                .flatMap(recipe -> recipe.materials().stream())
                .map(RecipeMaterial::material)
                .collect(Collectors.toUnmodifiableSet());
    }
}
