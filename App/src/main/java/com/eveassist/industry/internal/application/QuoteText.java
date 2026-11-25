package com.eveassist.industry.internal.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Writes a quote as the text and the CSV a buyer gets (decided with the developer on 2026-11-25). Money is whole ISK with thousands separators
 * ("266,053,000 ISK"), because a quote is rounded up to the quote step. A price per unit is given only for a quote of one product, since a price
 * per unit means nothing across several. The cost, the margin and the profit are never shown.
 */
final class QuoteText {

    private static final String CSV_LINE_END = "\r\n";

    private QuoteText() {
    }

    /** The text for a contract description or a message. */
    static String text(QuoteFacts quote) {
        var lines = new ArrayList<String>();
        lines.add("Quote " + quote.number() + (quote.buyer() == null ? "" : " for " + quote.buyer()));
        lines.add(quote.items().stream().map(item -> item.quantity() + " × " + item.name()).collect(Collectors.joining(", "))
                + ", delivered by contract");
        lines.add("Total: " + money(quote.price()) + " ISK");
        perUnit(quote).ifPresent(price -> lines.add("Per unit: " + money(price) + " ISK"));
        if (quote.expiresAt() != null) {
            lines.add("Valid until " + validUntil(quote));
        }
        if (quote.note() != null) {
            lines.add(quote.note());
        }
        return String.join("\n", lines);
    }

    /**
     * The same as a CSV (RFC 4180, with CRLF line ends): a row for each product, a row for the price per unit of a one-product quote, and a row for the
     * total, each saying the quote, the buyer, the expiry and the note so that a row can be read alone. Prices are plain whole numbers for a spreadsheet.
     */
    static String csv(QuoteFacts quote) {
        var rows = new ArrayList<List<String>>();
        rows.add(List.of("Quote", "Buyer", "Item", "Quantity", "Price (ISK)", "Valid until", "Note"));
        var common = List.of(String.valueOf(quote.number()), nullToEmpty(quote.buyer()));
        var tail = List.of(quote.expiresAt() == null ? "" : validUntil(quote), nullToEmpty(quote.note()));
        for (var item : quote.items()) {
            rows.add(row(common, item.name(), String.valueOf(item.quantity()), "", tail));
        }
        perUnit(quote).ifPresent(price -> rows.add(row(common, "Per unit", "", plain(price), tail)));
        rows.add(row(common, "Total", "", plain(quote.price()), tail));
        return rows.stream().map(QuoteText::csvLine).collect(Collectors.joining(CSV_LINE_END, "", CSV_LINE_END));
    }

    private static List<String> row(List<String> common, String item, String quantity, String price, List<String> tail) {
        var row = new ArrayList<String>(common);
        row.add(item);
        row.add(quantity);
        row.add(price);
        row.addAll(tail);
        return row;
    }

    /** The price of one unit, for a quote of one product only, to the whole ISK. */
    private static java.util.Optional<BigDecimal> perUnit(QuoteFacts quote) {
        if (quote.items().size() != 1 || quote.items().getFirst().quantity() < 1) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(quote.price().divide(BigDecimal.valueOf(quote.items().getFirst().quantity()), 0, RoundingMode.HALF_UP));
    }

    /**
     * The day the quote is valid until, in UTC. The page asks for a day and stores the start of the day after, so the second before is the day itself.
     */
    private static String validUntil(QuoteFacts quote) {
        return quote.expiresAt().minusSeconds(1).atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    /** Whole ISK with a comma between thousands, whatever the language of the server. */
    static String money(BigDecimal amount) {
        var format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(amount);
    }

    private static String plain(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private static String csvLine(List<String> fields) {
        return fields.stream().map(QuoteText::csvField).collect(Collectors.joining(","));
    }

    /**
     * One field. A field that starts with =, +, - or @ (or a tab or a return) is read by a spreadsheet as a formula, and the buyer's name and the note are
     * text somebody typed, so a quote is put in front of it. A field with a comma, a quote or a line end is wrapped in quotes, with its quotes doubled.
     */
    static String csvField(String field) {
        var safe = !field.isEmpty() && "=+-@\t\r".indexOf(field.charAt(0)) >= 0 ? "'" + field : field;
        return safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")
                ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }

    /** A name for the file: the project's name as lower case letters, digits and dashes, and the quote's number. */
    static String fileName(String projectName, int number) {
        var slug = projectName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40).replaceAll("-+$", "");
        }
        return "quote-" + (slug.isEmpty() ? "project" : slug) + "-" + number + ".csv";
    }
}
