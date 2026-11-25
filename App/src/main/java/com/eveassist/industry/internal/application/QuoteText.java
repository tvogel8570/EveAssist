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

    /**
     * The text for a contract description or a message: who it is for and until when, the note, then a line for each product with its price per unit and its
     * extension, and the total. It is the layout of the CSV (decided with the developer on 2026-11-25). ISK is written once, on the grand total, and not on
     * each price and extension.
     */
    static String text(QuoteFacts quote) {
        var lines = new ArrayList<String>();
        lines.add("Quote " + quote.number() + (quote.buyer() == null ? "" : " for " + quote.buyer()));
        if (quote.expiresAt() != null) {
            lines.add("Valid until " + validUntil(quote));
        }
        if (quote.note() != null) {
            lines.add(quote.note());
        }
        lines.add("Delivered by contract");
        lines.add("");
        for (var line : quote.lines()) {
            lines.add(line.name() + ": " + line.quantity() + " \u00d7 " + money(line.unitPrice()) + " = " + money(line.extension()));
        }
        lines.add("Total: " + money(quote.price()) + " ISK");
        return String.join("\n", lines);
    }

    /**
     * The same as a CSV (RFC 4180, with CRLF line ends), laid out as a sheet: the quote, the buyer, the expiry and the note in a row under their headers, an empty
     * row, then the products under Item, Quantity, Price (ISK) and Extension (ISK), and a Total row with the total under Extension. Prices are plain whole
     * numbers, for a spreadsheet to add up, and the expiry is an ISO date, which a spreadsheet reads as a date of its own locale.
     */
    static String csv(QuoteFacts quote) {
        var rows = new ArrayList<List<String>>();
        rows.add(List.of("Quote", "Buyer", "Valid until", "Note"));
        rows.add(List.of(String.valueOf(quote.number()), nullToEmpty(quote.buyer()), quote.expiresAt() == null ? "" : validUntil(quote),
                nullToEmpty(quote.note())));
        rows.add(List.of());
        rows.add(List.of("Item", "Quantity", "Price (ISK)", "Extension (ISK)"));
        for (var line : quote.lines()) {
            rows.add(List.of(line.name(), String.valueOf(line.quantity()), String.valueOf(line.unitPrice()), String.valueOf(line.extension())));
        }
        rows.add(List.of("Total", "", "", plain(quote.price())));
        return rows.stream().map(QuoteText::csvLine).collect(Collectors.joining(CSV_LINE_END, "", CSV_LINE_END));
    }

    /**
     * The day the quote is valid until, in UTC. The page asks for a day and stores the start of the day after, so the second before is the day itself.
     */
    private static String validUntil(QuoteFacts quote) {
        return quote.expiresAt().minusSeconds(1).atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    /** Whole ISK of a number, with its commas. */
    static String money(long amount) {
        return money(BigDecimal.valueOf(amount));
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
