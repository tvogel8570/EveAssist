package com.eveassist.inventory.internal;

import com.eveassist.characters.LinkedCharacter;
import com.eveassist.characters.LinkedCharacters;
import com.eveassist.identity.Viewer;
import com.eveassist.inventory.internal.BrowseFilter.Dimension;
import com.eveassist.inventory.internal.BrowseView.Chip;
import com.eveassist.inventory.internal.BrowseView.Choice;
import com.eveassist.inventory.internal.BrowseView.FacetView;
import com.eveassist.inventory.internal.BrowseView.ItemView;
import com.eveassist.inventory.internal.BrowseView.Segment;
import com.eveassist.inventory.internal.BrowseView.WhereLine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

/**
 * Answers the inventory page for one viewer. The viewer's own linked characters are the only ones asked about, and each
 * has a colour that stays the same on every page and in every bar: the colour comes from its place among the viewer's
 * characters in the order they were first linked, so linking a character never changes the colour of another. Removing
 * one moves the colours of those linked after it, and more than 24 characters share colours.
 */
@Service
public class BrowseService {

    /** The colours the bars use. They are classes in app.css, so no style is written into a page. */
    static final int COLORS = 24;

    private final LinkedCharacters linked;
    private final BrowseQueries queries;
    private final CorporationRepository corporations;
    private final MessageSource messages;

    BrowseService(LinkedCharacters linked, BrowseQueries queries, CorporationRepository corporations,
            MessageSource messages) {
        this.linked = linked;
        this.queries = queries;
        this.corporations = corporations;
        this.messages = messages;
    }

    /**
     * The holders of the viewer's items with their colours: the characters in the order they were linked, which fixes
     * their colours, and then the corporations by name, when the account has the Corporation assets feature on. The
     * name of the method is older than the corporations: it holds both, and the page says "holder" where it must.
     */
    private Map<UUID, BrowseView.CharacterRef> characters(Viewer viewer) {
        var sorted = linked.of(viewer).stream()
                .sorted(Comparator.comparing(LinkedCharacter::linkedAt).thenComparingLong(LinkedCharacter::eveCharacterId))
                .toList();
        var result = new java.util.LinkedHashMap<UUID, BrowseView.CharacterRef>();
        for (int i = 0; i < sorted.size(); i++) {
            result.put(sorted.get(i).id(), new BrowseView.CharacterRef(sorted.get(i).id(), sorted.get(i).name(),
                    i % COLORS, false));
        }
        if (!sorted.isEmpty() && linked.featuresOn(viewer).contains(com.eveassist.characters.Feature.CORPORATION_ASSETS)) {
            var own = corporations.listFor(viewer.accountId().value());
            for (int i = 0; i < own.size(); i++) {
                result.put(own.get(i).id(), new BrowseView.CharacterRef(own.get(i).id(), own.get(i).name(),
                        (sorted.size() + i) % COLORS, true));
            }
        }
        return result;
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }

    public BrowseView browse(Viewer viewer, BrowseFilter filter) {
        var characters = characters(viewer);
        var ids = characters.keySet().toArray(UUID[]::new);
        if (ids.length == 0) {
            return new BrowseView(List.of(), 0, 0, false, false, filter.showCharacters(), List.of(), none(filter), "",
                    "", filter.unfiltered(), false, false);
        }
        var rows = queries.items(ids, filter);
        var summary = queries.summary(ids, filter);
        var items = rows.stream().map(row -> item(row, characters)).toList();
        return new BrowseView(items, summary.itemTypes(), summary.units(),
                moreAvailable(summary.itemTypes(), items.size(), filter.limit()),
                capped(summary.itemTypes(), items.size(), filter.limit()), filter.showCharacters(), chips(ids, characters, filter), none(filter), filter.toQuery(),
                filter.withLimit(filter.limit() + BrowseFilter.FIRST_PAGE).toQuery(), filter.unfiltered(), true,
                characters.values().stream().anyMatch(BrowseView.CharacterRef::corporation));
    }

    /** More item types than shown, and a bigger page would show them. */
    static boolean moreAvailable(int itemTypes, int shown, int limit) {
        return itemTypes > shown && limit < BrowseFilter.MOST_ITEMS;
    }

    /** More item types than shown, but the page already lists the most it ever will: only a narrower filter helps. */
    static boolean capped(int itemTypes, int shown, int limit) {
        return itemTypes > shown && limit >= BrowseFilter.MOST_ITEMS;
    }

    private static String none(BrowseFilter filter) {
        var cleared = BrowseFilter.none();
        return new BrowseFilter("", cleared.categories(), cleared.groups(), cleared.characters(), cleared.places(),
                cleared.regions(), cleared.held(), 0, filter.sort(), filter.showCharacters(), BrowseFilter.FIRST_PAGE)
                .toQuery();
    }

    private ItemView item(BrowseQueries.ItemRow row, Map<UUID, BrowseView.CharacterRef> characters) {
        var segments = new ArrayList<Segment>();
        double at = 0;
        for (int i = 0; i < row.holders().size(); i++) {
            var holder = characters.get(row.holders().get(i));
            if (holder == null) {
                continue;
            }
            double width = 100.0 * row.held().get(i) / row.total();
            segments.add(new Segment(holder.name(), holder.color(), row.held().get(i), percent(at), percent(width)));
            at += width;
        }
        return new ItemView(row.typeId(), row.name(), row.groupName(), row.categoryName(), row.total(), row.places(),
                row.hasBlueprintCopy(), row.hasBlueprintOriginal(), segments, segments.size(),
                segments.isEmpty() ? "" : segments.getFirst().character());
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private List<Chip> chips(UUID[] ids, Map<UUID, BrowseView.CharacterRef> characters, BrowseFilter filter) {
        var chips = new ArrayList<Chip>();
        for (var dimension : Dimension.values()) {
            var values = filter.valuesOf(dimension);
            if (values.isEmpty()) {
                continue;
            }
            Map<String, String> names = switch (dimension) {
                case CHARACTER -> characters.values().stream().collect(Collectors.toMap(c -> c.id().toString(),
                        BrowseView.CharacterRef::name));
                case HOW -> values.stream().collect(Collectors.toMap(Function.identity(),
                        value -> text("inventory.held." + value)));
                default -> queries.labels(ids, dimension, values);
            };
            var worded = values.stream().map(value -> names.getOrDefault(value, unknown(dimension, value))).toList();
            chips.add(new Chip(dimension.parameter, chipText(title(dimension, characters), worded),
                    filter.without(dimension).toQuery()));
        }
        if (filter.minTotal() > 0) {
            chips.add(new Chip("min", chipText(text("inventory.filter.min"),
                    List.of(String.format(Locale.ROOT, "%,d", filter.minTotal()))), filter.withoutMinimum().toQuery()));
        }
        return chips;
    }

    /** The name of a filter column. The character column also lists corporations when the account has any. */
    private String title(Dimension dimension, Map<UUID, BrowseView.CharacterRef> holders) {
        var corporations = dimension == Dimension.CHARACTER
                && holders.values().stream().anyMatch(BrowseView.CharacterRef::corporation);
        return text("inventory.filter." + dimension.parameter + (corporations ? ".withCorporations" : ""));
    }

    /** The names of all the filter columns for the Add filter menu, with one look at the viewer's holders. */
    public Map<Dimension, String> columnTitles(Viewer viewer) {
        var holders = characters(viewer);
        var titles = new java.util.EnumMap<Dimension, String>(Dimension.class);
        for (var dimension : Dimension.values()) {
            titles.put(dimension, title(dimension, holders));
        }
        return titles;
    }

    /** "Category: Module or Charge", or "Character: 6 selected" when there are too many to read. */
    private String chipText(String title, List<String> values) {
        return values.size() <= 2 ? title + ": " + String.join(" " + text("inventory.or") + " ", values)
                : title + ": " + text("inventory.selected", values.size());
    }

    private String unknown(Dimension dimension, String value) {
        if (dimension == Dimension.PLACE) {
            return placeName(null, value);
        }
        if (dimension == Dimension.CHARACTER) {
            return text("inventory.character.unlinked");
        }
        return dimension == Dimension.REGION && "0".equals(value) ? text("inventory.region.unknown") : value;
    }

    private String placeName(String name, String id) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        try {
            return Long.parseLong(id) >= StructureRepository.FIRST_STRUCTURE_ID
                    ? text("inventory.place.unknownStructure", id) : text("inventory.place.unknownLocation", id);
        } catch (NumberFormatException e) {
            return id;
        }
    }

    /** Where one item type is, within the filter, as lines for the expanded row. */
    public List<WhereLine> where(Viewer viewer, BrowseFilter filter, int typeId) {
        var characters = characters(viewer);
        if (characters.isEmpty()) {
            return List.of();
        }
        var hangars = corporations.divisionNames(characters.values().stream().filter(BrowseView.CharacterRef::corporation)
                .map(BrowseView.CharacterRef::id).toList());
        return queries.where(characters.keySet().toArray(UUID[]::new), filter, typeId).stream().map(row -> {
            var who = row.byCharacter().entrySet().stream().filter(e -> characters.containsKey(e.getKey()))
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .map(e -> characters.get(e.getKey()).name() + " " + String.format(Locale.ROOT, "%,d", e.getValue()))
                    .toList();
            var shown = who.size() > 3 ? text("inventory.where.andMore", String.join(", ", who.subList(0, 3)), who.size() - 3)
                    : String.join(", ", who);
            var place = placeName(row.placeName(), String.valueOf(row.locationId()));
            return new WhereLine(place, row.placeName() == null, row.regionName() == null ? "" : row.regionName(),
                    howHeld(row), row.held().name(), hangar(row.division(), row.byCharacter().keySet(), hangars), shown,
                    row.total());
        }).toList();
    }

    /**
     * How an item is held. An item in a container says which one: the name the player gave it, in quotes, or else what
     * kind of container it is. Anything else, and a container whose type is not known, gets the plain wording.
     */
    private String howHeld(BrowseQueries.WhereRow row) {
        if (row.held() == Held.IN_CONTAINER) {
            var name = row.containerName();
            // exactly as the player typed it, spaces included: they use them to control how the game sorts
            if (name != null && !name.isEmpty()) {
                return text("inventory.where.inContainer", "\"" + name + "\"");
            }
            if (row.containerType() != null) {
                return text("inventory.where.inContainer", row.containerType());
            }
        }
        return text("inventory.held." + row.held().name());
    }

    /**
     * "Production hangar" for a corporation's division, named as the corporation named it, or "Hangar 3" when it gave no
     * name. Empty for a character's items. A line with a division holds one corporation's items (see {@link BrowseQueries}).
     */
    private String hangar(Integer division, java.util.Set<UUID> holders, Map<UUID, Map<Integer, String>> names) {
        if (division == null) {
            return "";
        }
        for (var holder : holders) {
            var name = names.getOrDefault(holder, Map.of()).get(division);
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return text("inventory.hangar.number", division);
    }

    /** The values a column offers, for the list opened by Add filter. */
    public FacetView facet(Viewer viewer, Dimension dimension, BrowseFilter filter, String search) {
        var characters = characters(viewer);
        var title = title(dimension, characters);
        if (characters.isEmpty()) {
            return new FacetView(dimension.parameter, title, List.of(), true, false);
        }
        var chosen = filter.valuesOf(dimension);
        var needle = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        var found = queries.facet(characters.keySet().toArray(UUID[]::new), dimension, filter, search);
        var choices = found.stream().map(facet -> {
            var label = switch (dimension) {
                case CHARACTER -> {
                    var character = characters.get(UUID.fromString(facet.value()));
                    yield character == null ? facet.value() : character.name();
                }
                case HOW -> text("inventory.held." + facet.value());
                case PLACE -> placeName(facet.label(), facet.value());
                case REGION -> "0".equals(facet.value()) ? text("inventory.region.unknown") : facet.label();
                default -> facet.label();
            };
            return new Choice(facet.value(), label, facet.itemTypes(), chosen.contains(facet.value()));
        }).filter(choice -> needle.isEmpty() || choice.label().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(Choice::label, String.CASE_INSENSITIVE_ORDER)).toList();
        return new FacetView(dimension.parameter, title, choices, dimension != Dimension.HOW,
                found.size() >= BrowseQueries.MOST_FACET_VALUES);
    }

    /** True when the export reached its size limit, so lines after the last one may be missing. */
    static boolean cutShort(int rows, int most) {
        return rows >= most;
    }

    /**
     * Every item type the filter lets through, one line each with the name, a tab and the total, for Copy as text. It
     * is the whole list, not the page that is on screen, up to a limit that no real list reaches.
     */
    public String copyText(Viewer viewer, BrowseFilter filter) {
        var characters = characters(viewer);
        if (characters.isEmpty()) {
            return "";
        }
        var out = new StringBuilder();
        for (var row : queries.items(characters.keySet().toArray(UUID[]::new), filter, MOST_COPIED)) {
            out.append(row.name().replaceAll("[\\t\\r\\n]+", " ")).append('\t').append(row.total()).append('\n');
        }
        return out.toString();
    }

    /** The most item types Copy as text gives. */
    static final int MOST_COPIED = 10_000;

    /** The lines of the CSV export, worded. */
    public List<String[]> export(Viewer viewer, BrowseFilter filter) {
        var characters = characters(viewer);
        var lines = new ArrayList<String[]>();
        if (characters.isEmpty()) {
            return lines;
        }
        var hangars = corporations.divisionNames(characters.values().stream().filter(BrowseView.CharacterRef::corporation)
                .map(BrowseView.CharacterRef::id).toList());
        var rows = queries.export(characters.keySet().toArray(UUID[]::new), filter);
        for (var row : rows) {
            var character = characters.get(row.character());
            lines.add(new String[] {row.item(), row.category(), row.group(), character == null ? "" : character.name(),
                    row.place(), row.region(), text("inventory.held." + row.held().name()),
                    String.valueOf(row.quantity()), row.blueprint(),
                    hangar(row.division(), java.util.Set.of(row.character()), hangars),
                    row.container() == null ? "" : row.container()});
        }
        if (cutShort(rows.size(), BrowseQueries.MOST_EXPORT_ROWS)) {
            lines.add(new String[] {text("inventory.csv.cut", BrowseQueries.MOST_EXPORT_ROWS)});
        }
        return lines;
    }
}
