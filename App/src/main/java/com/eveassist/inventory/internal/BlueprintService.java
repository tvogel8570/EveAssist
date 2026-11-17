package com.eveassist.inventory.internal;

import com.eveassist.characters.Feature;
import com.eveassist.characters.LinkedCharacter;
import com.eveassist.characters.LinkedCharacters;
import com.eveassist.identity.Viewer;
import com.eveassist.sde.ItemType;
import com.eveassist.sde.Recipe;
import com.eveassist.sde.RecipeMaterial;
import com.eveassist.sde.SdeService;
import com.eveassist.sde.TypeId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Answers the Blueprints page using blueprint rows for ME/TE/runs and inventory rows for place names. */
@Service
public class BlueprintService {

    public record Blueprint(long itemId, int typeId, String name, String kind, int materialEfficiency,
            int timeEfficiency, long quantity, int runs, UUID holderId, String holderName, boolean corporation,
            int holderColor, String status, String place, String held, String hangar, String container,
            boolean matched, int tech, String variant) {
    }

    public record Holder(UUID id, String name, boolean corporation) {
    }

    public record Page(List<Blueprint> items, List<Holder> holders, List<BlueprintFetcher.HolderStatus> statuses,
            BlueprintFilter filter, boolean hasSources, long total, Map<Integer, Long> techCounts) {

        /** How many blueprints the buttons above the table add up to: the list under every filter but the tech level. */
        public long techTotal() {
            return techCounts.values().stream().mapToLong(Long::longValue).sum();
        }

        /**
         * A short text that stands for everything this page shows. The page sends it back with the status poll, and
         * the table is fetched again only when it no longer matches, so a read that changed nothing sends nothing.
         */
        public String fingerprint() {
            return Integer.toHexString(java.util.Objects.hash(items, holders, total, hasSources, techCounts));
        }

        /** The tech levels that have a button above the table, in the order shown: 1, 2, 3, and then none. */
        public List<Integer> techLevels() {
            return java.util.stream.Stream.of(1, 2, 3, 0)
                    .filter(level -> techCounts.containsKey(level) || java.util.Objects.equals(filter.tech(), level))
                    .toList();
        }

        /** Whether the list stops before the end and a page more can be shown. */
        public boolean more() {
            return total > items.size() && filter.limit() < BlueprintFilter.MOST_ITEMS;
        }

        /** Whether the list stops before the end and there is no page more: the filters have to narrow it. */
        public boolean capped() {
            return total > items.size() && filter.limit() >= BlueprintFilter.MOST_ITEMS;
        }
    }

    public record Material(String name, long baseQuantity, long blueprintQuantity, long standardQuantity) {
    }

    public record RecipeDetail(String product, int unitsPerRun, long secondsAtBlueprint, long secondsAtStandard,
            List<Material> materials) {
    }

    public record Detail(Blueprint blueprint, List<Blueprint> sameType, RecipeDetail recipe, String returnQuery) {
    }

    private final LinkedCharacters linked;
    private final BlueprintFetcher fetcher;
    private final BlueprintQueries queries;
    private final SdeService sde;
    private final HolderColors holderColors;

    BlueprintService(LinkedCharacters linked, BlueprintFetcher fetcher,
            BlueprintQueries queries, SdeService sde, HolderColors holderColors) {
        this.holderColors = holderColors;
        this.linked = linked;
        this.fetcher = fetcher;
        this.queries = queries;
        this.sde = sde;
    }

    private static final BlueprintFilter ALL = BlueprintFilter.all();

    /** The ids a viewer may read, from the features turned on and the corporations they hold. */
    private record Scope(java.util.Set<Feature> features, List<LinkedCharacter> characters,
            List<CorporationRepository.Corporation> ownCorporations, UUID[] characterIds, UUID[] corporationIds,
            UUID[] structureCharacterIds, Map<UUID, Integer> colors) {
    }

    private Scope scope(Viewer viewer) {
        var features = linked.featuresOn(viewer);
        var characters = linked.of(viewer);
        var characterIds = features.contains(Feature.BLUEPRINTS)
                ? characters.stream().map(LinkedCharacter::id).toArray(UUID[]::new) : new UUID[0];
        var ownCorporations = features.contains(Feature.CORPORATION_BLUEPRINTS)
                ? fetcher.corporationsWithDirector(viewer) : List.<CorporationRepository.Corporation>of();
        var corporationIds = ownCorporations.stream().map(CorporationRepository.Corporation::id).toArray(UUID[]::new);
        var structureCharacterIds = characters.stream().map(LinkedCharacter::id).toArray(UUID[]::new);
        return new Scope(features, characters, ownCorporations, characterIds, corporationIds, structureCharacterIds,
                holderColors.of(viewer));
    }

    private List<Blueprint> rows(Scope scope, BlueprintFilter filter, java.util.Collection<Long> itemIds) {
        return queries.list(scope.characterIds(), scope.corporationIds(), scope.structureCharacterIds(), filter, itemIds)
                .stream().map(row -> blueprint(row, scope.colors())).toList();
    }

    public Page browse(Viewer viewer, BlueprintFilter filter) {
        var scope = scope(viewer);
        var rows = rows(scope, filter, null);
        var total = rows.size() < filter.limit() ? rows.size()
                : queries.count(scope.characterIds(), scope.corporationIds(), scope.structureCharacterIds(), filter);
        var holders = new ArrayList<Holder>();
        if (scope.features().contains(Feature.BLUEPRINTS)) {
            scope.characters().stream()
                    .sorted(Comparator.comparing(LinkedCharacter::name, String.CASE_INSENSITIVE_ORDER))
                    .forEach(character -> holders.add(new Holder(character.id(), character.name(), false)));
        }
        scope.ownCorporations().stream().sorted(Comparator.comparing(CorporationRepository.Corporation::name,
                        String.CASE_INSENSITIVE_ORDER))
                .forEach(corporation -> holders.add(new Holder(corporation.id(), corporation.name(), true)));
        return new Page(rows, List.copyOf(holders), fetcher.status(viewer), filter,
                scope.characterIds().length > 0 || scope.corporationIds().length > 0, total,
                queries.techCounts(scope.characterIds(), scope.corporationIds(), scope.structureCharacterIds(), filter));
    }

    public Optional<Detail> detail(Viewer viewer, long itemId, BlueprintFilter filter) {
        var scope = scope(viewer);
        var blueprint = rows(scope, ALL, List.of(itemId)).stream().findFirst();
        if (blueprint.isEmpty()) {
            return Optional.empty();
        }
        var row = blueprint.get();
        var sameType = rows(scope, new BlueprintFilter("", row.typeId(), "", 0, 0, null, false, "", null, "type", "asc", BlueprintFilter.NO_LIMIT), null)
                .stream().filter(other -> other.itemId() != row.itemId()).toList();
        return Optional.of(new Detail(row, sameType, recipe(row), filter.query()));
    }

    /** Selected ids are resolved again against only the viewer's enabled holders before export or navigation. */
    public List<Blueprint> selected(Viewer viewer, List<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return List.of();
        }
        var found = rows(scope(viewer), ALL, itemIds.stream().distinct().toList());
        // an item moved between a character and its corporation can be in both lists until one is read again
        var byId = found.stream().collect(Collectors.toMap(Blueprint::itemId, item -> item, (first, second) -> first));
        return itemIds.stream().distinct().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    private Blueprint blueprint(BlueprintQueries.Row row, Map<UUID, Integer> colors) {
        return new Blueprint(row.itemId(), row.typeId(), row.typeName(), row.kind(), row.materialEfficiency(),
                row.timeEfficiency(), row.quantity(), row.runs(), row.holderId(), row.holderName(), row.corporation(),
                colors.getOrDefault(row.holderId(), 0), "NOT_READ", row.place(), row.held(), row.hangar(), row.container(),
                row.matched(), row.tech(), row.variant());
    }

    private RecipeDetail recipe(Blueprint blueprint) {
        return sde.recipeOfBlueprint(new TypeId(blueprint.typeId())).map(recipe -> recipe(recipe, blueprint))
                .orElse(null);
    }

    private RecipeDetail recipe(Recipe recipe, Blueprint blueprint) {
        var materials = recipe.materials().stream().map(material -> material(material, blueprint.materialEfficiency()))
                .toList();
        return new RecipeDetail(itemName(recipe.product()), recipe.unitsPerRun(), seconds(recipe.secondsPerRun(),
                blueprint.timeEfficiency()), seconds(recipe.secondsPerRun(), 20), materials);
    }

    private Material material(RecipeMaterial material, int me) {
        var quantity = material.quantity();
        return new Material(itemName(material.material()), quantity,
                materialsNeeded(quantity, 1, me), materialsNeeded(quantity, 1, 10));
    }

    private String itemName(TypeId id) {
        return sde.itemType(id).map(ItemType::name).orElse("Type " + id.value());
    }

    private static long materialsNeeded(long baseQuantity, long runs, int me) {
        var reduced = Math.ceilDiv(Math.multiplyExact(Math.multiplyExact(baseQuantity, runs), 100L - me), 100L);
        return Math.max(runs, reduced);
    }

    private static long seconds(long baseSeconds, int te) {
        return Math.ceilDiv(Math.multiplyExact(baseSeconds, 100L - te), 100L);
    }
}