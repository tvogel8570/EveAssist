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
 * characters in the order of their EVE ids, which only grows when one is linked.
 */
@Service
public class BrowseService {

    /** The colours the bars use. They are classes in app.css, so no style is written into a page. */
    static final int COLORS = 24;

    private final LinkedCharacters linked;
    private final BrowseQueries queries;
    private final MessageSource messages;

    BrowseService(LinkedCharacters linked, BrowseQueries queries, MessageSource messages) {
        this.linked = linked;
        this.queries = queries;
        this.messages = messages;
    }

    /** The viewer's characters with their colours, in the order that fixes the colours. */
    private Map<UUID, BrowseView.CharacterRef> characters(Viewer viewer) {
        var sorted = linked.of(viewer).stream().sorted(Comparator.comparingLong(LinkedCharacter::eveCharacterId))
                .toList();
        var result = new java.util.LinkedHashMap<UUID, BrowseView.CharacterRef>();
        for (int i = 0; i < sorted.size(); i++) {
            result.put(sorted.get(i).id(), new BrowseView.CharacterRef(sorted.get(i).id(), sorted.get(i).name(),
                    i % COLORS));
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
            return new BrowseView(List.of(), 0, 0, false, filter.showCharacters(), List.of(), none(filter), "", "",
                    filter.unfiltered(), false);
        }
        var rows = queries.items(ids, filter);
        var summary = queries.summary(ids, filter);
        var items = rows.stream().map(row -> item(row, characters)).toList();
        return new BrowseView(items, summary.itemTypes(), summary.units(), summary.itemTypes() > items.size(),
                filter.showCharacters(), chips(ids, characters, filter), none(filter), filter.toQuery(),
                filter.withLimit(filter.limit() + BrowseFilter.FIRST_PAGE).toQuery(), filter.unfiltered(), true);
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
            chips.add(new Chip(dimension.parameter, chipText(text("inventory.filter." + dimension.parameter), worded),
                    filter.without(dimension).toQuery()));
        }
        if (filter.minTotal() > 0) {
            chips.add(new Chip("min", chipText(text("inventory.filter.min"),
                    List.of(String.format(Locale.ROOT, "%,d", filter.minTotal()))), filter.withoutMinimum().toQuery()));
        }
        return chips;
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
        return queries.where(characters.keySet().toArray(UUID[]::new), filter, typeId).stream().map(row -> {
            var who = row.byCharacter().entrySet().stream().filter(e -> characters.containsKey(e.getKey()))
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .map(e -> characters.get(e.getKey()).name() + " " + String.format(Locale.ROOT, "%,d", e.getValue()))
                    .toList();
            var shown = who.size() > 3 ? text("inventory.where.andMore", String.join(", ", who.subList(0, 3)), who.size() - 3)
                    : String.join(", ", who);
            var place = placeName(row.placeName(), String.valueOf(row.locationId()));
            return new WhereLine(place, row.placeName() == null, row.regionName() == null ? "" : row.regionName(),
                    text("inventory.held." + row.held().name()), shown, row.total());
        }).toList();
    }

    /** The values a column offers, for the list opened by Add filter. */
    public FacetView facet(Viewer viewer, Dimension dimension, BrowseFilter filter, String search) {
        var characters = characters(viewer);
        var title = text("inventory.filter." + dimension.parameter);
        if (characters.isEmpty()) {
            return new FacetView(dimension.parameter, title, List.of(), true);
        }
        var chosen = filter.valuesOf(dimension);
        var needle = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        var choices = queries.facet(characters.keySet().toArray(UUID[]::new), dimension, filter).stream().map(facet -> {
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
        return new FacetView(dimension.parameter, title, choices, dimension != Dimension.HOW);
    }

    /** The lines of the CSV export, worded. */
    public List<String[]> export(Viewer viewer, BrowseFilter filter) {
        var characters = characters(viewer);
        var lines = new ArrayList<String[]>();
        if (characters.isEmpty()) {
            return lines;
        }
        for (var row : queries.export(characters.keySet().toArray(UUID[]::new), filter)) {
            var character = characters.get(row.character());
            lines.add(new String[] {row.item(), row.category(), row.group(), character == null ? "" : character.name(),
                    row.place(), row.region(), text("inventory.held." + row.held().name()),
                    String.valueOf(row.quantity()), row.blueprint()});
        }
        return lines;
    }
}
