package com.eveassist.inventory.internal;

import java.util.List;
import java.util.UUID;

/**
 * What the inventory page shows for a filter. Plain data for the templates: every text is already worded.
 *
 * @param more   true when there are more item types than are shown and asking for more would show them
 * @param capped true when there are more than the most the page will ever list, so only a narrower filter helps
 */
public record BrowseView(List<ItemView> items, int itemTypes, long units, boolean more, boolean capped,
        boolean showCharacters,
        List<Chip> chips, String clearAllQuery, String exportQuery, String moreQuery, boolean unfiltered,
        boolean hasAssets, boolean corporations) {

    /** One item type: its total, and who holds it as the pieces of one bar. */
    public record ItemView(int typeId, String name, String groupName, String categoryName, long total, int places,
            boolean blueprintCopy, boolean blueprintOriginal, List<Segment> segments, int holderCount,
            String biggestHolder) {
    }

    /**
     * One character's share of an item type.
     *
     * @param color    which of the colours the character has, the same everywhere on the page
     * @param start    where the piece starts along the bar, as a number from 0 to 100
     * @param width    how wide it is, as a number from 0 to 100
     */
    public record Segment(String character, int color, long quantity, String start, String width) {
    }

    /**
     * A chosen filter, shown as a chip that can be taken away.
     *
     * @param dimension   the column, which opens its list again
     * @param text        what the chip says, such as "Category: Module or Charge"
     * @param removeQuery the address of the page without this filter
     */
    public record Chip(String dimension, String text, String removeQuery) {
    }

    /** One value of a column, offered in the filter list. */
    public record Choice(String value, String label, int itemTypes, boolean checked) {
    }

    /** The list of values for one column. */
    public record FacetView(String dimension, String title, List<Choice> choices, boolean searchable,
            boolean truncated) {
    }

    /** One place an item type is held, and how. */
    public record WhereLine(String place, boolean placeUnknown, String region, String held, String hangar,
            String characters, long total) {
    }

    /** A holder of items, a character or a corporation, with its colour. */
    public record CharacterRef(UUID id, String name, int color, boolean corporation) {
    }
}
