package com.eveassist.industry.internal.project.quote.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** The text and the CSV a buyer gets, in the layout of the developer's sheet of 2026-11-25. They are worked out by hand. */
class QuoteTextTest {

    /** The page stores "valid until 2026-12-02" as the start of the next day. */
    private static final Instant EXPIRES = Instant.parse("2026-12-03T00:00:00Z");

    private static QuoteFacts ishtars() {
        return new QuoteFacts(1, "Kestrel Dawn", "Delivery to Perimeter.", EXPIRES, new BigDecimal("266053000.00"),
                List.of(new QuoteFacts.Line("Ishtar", 5, 53_210_600, 266_053_000)), false);
    }

    private static QuoteFacts sheet() {
        return new QuoteFacts(1, "bogus", "notes here", EXPIRES, new BigDecimal("8"),
                List.of(new QuoteFacts.Line("Vexor", 1, 2, 2), new QuoteFacts.Line("Legion", 2, 3, 6)), false);
    }

    @Test
    void theTextHasAHeaderThenALineForEachProductWithItsPriceAndExtensionThenTheTotal() {
        assertThat(QuoteText.text(sheet())).isEqualTo("""
                Quote 1 for bogus
                Valid until 2026-12-02
                notes here
                Delivered by contract

                Vexor: 1 × 2 = 2
                Legion: 2 × 3 = 6
                Total: 8 ISK""");
        assertThat(QuoteText.text(ishtars())).isEqualTo("""
                Quote 1 for Kestrel Dawn
                Valid until 2026-12-02
                Delivery to Perimeter.
                Delivered by contract

                Ishtar: 5 × 53,210,600 = 266,053,000
                Total: 266,053,000 ISK""");
    }

    @Test
    void aQuoteWithNoBuyerNoExpiryAndNoNoteLeavesThoseLinesOut() {
        var quote = new QuoteFacts(3, null, null, null, new BigDecimal("1500000"), List.of(new QuoteFacts.Line("Vexor", 1, 1_500_000, 1_500_000)),
                false);

        assertThat(QuoteText.text(quote)).isEqualTo("""
                Quote 3
                Delivered by contract

                Vexor: 1 × 1,500,000 = 1,500,000
                Total: 1,500,000 ISK""");
    }

    @Test
    void theCsvIsTheDevelopersSheet() {
        assertThat(QuoteText.csv(sheet())).isEqualTo(String.join("\r\n",
                "Quote,Buyer,Valid until,Note",
                "1,bogus,2026-12-02,notes here",
                "",
                "Item,Quantity,Price (ISK),Extension (ISK)",
                "Vexor,1,2,2",
                "Legion,2,3,6",
                "Total,,,8", ""));
    }

    @Test
    void aCsvWithNoBuyerNoExpiryAndNoNoteHasThoseCellsEmpty() {
        var quote = new QuoteFacts(3, null, null, null, new BigDecimal("1500000"), List.of(new QuoteFacts.Line("Vexor", 1, 1_500_000, 1_500_000)),
                false);

        assertThat(QuoteText.csv(quote)).isEqualTo(String.join("\r\n",
                "Quote,Buyer,Valid until,Note",
                "3,,,",
                "",
                "Item,Quantity,Price (ISK),Extension (ISK)",
                "Vexor,1,1500000,1500000",
                "Total,,,1500000", ""));
    }

    @Test
    void theExtensionOfTheBiggestLineMayCarryTheLeftoverAndTheTotalStillAddsUp() {
        var quote = new QuoteFacts(2, "Buyer", null, null, new BigDecimal("16000001"),
                List.of(new QuoteFacts.Line("Vexor", 1, 4_000_000, 4_000_000), new QuoteFacts.Line("Legion", 2, 6_000_000, 12_000_001)), false);

        assertThat(QuoteText.text(quote)).contains("Legion: 2 × 6,000,000 = 12,000,001").endsWith("Total: 16,000,001 ISK");
        assertThat(QuoteText.csv(quote)).contains("Legion,2,6000000,12000001\r\n").endsWith("Total,,,16000001\r\n");
    }

    @Test
    void moneyKeepsItsCommasWhateverTheServersLanguage() {
        var before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertThat(QuoteText.money(new BigDecimal("1234567.5"))).isEqualTo("1,234,568");
            assertThat(QuoteText.text(ishtars())).contains("Total: 266,053,000 ISK").contains("5 × 53,210,600 = 266,053,000");
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    void aFieldWithACommaAQuoteOrALineEndIsQuotedWithItsQuotesDoubled() {
        assertThat(QuoteText.csvField("plain")).isEqualTo("plain");
        assertThat(QuoteText.csvField("a, b")).isEqualTo("\"a, b\"");
        assertThat(QuoteText.csvField("say \"no\"")).isEqualTo("\"say \"\"no\"\"\"");
        assertThat(QuoteText.csvField("one\ntwo")).isEqualTo("\"one\ntwo\"");
        assertThat(QuoteText.csvField("")).isEmpty();
    }

    @Test
    void aFieldThatASpreadsheetWouldReadAsAFormulaIsPutRightWithAQuote() {
        // the buyer's name and the note are text somebody typed
        assertThat(QuoteText.csvField("=1+1")).isEqualTo("'=1+1");
        assertThat(QuoteText.csvField("+1")).isEqualTo("'+1");
        assertThat(QuoteText.csvField("-2")).isEqualTo("'-2");
        assertThat(QuoteText.csvField("@sum")).isEqualTo("'@sum");
        assertThat(QuoteText.csvField("\t=x")).isEqualTo("'\t=x");
        assertThat(QuoteText.csvField("=HYPERLINK(\"http://x\",\"y\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\",\"\"y\"\")\"");
        // a field with an equals sign inside it is not a formula
        assertThat(QuoteText.csvField("a=b")).isEqualTo("a=b");

        var quote = new QuoteFacts(1, "=cmd", "@note", null, new BigDecimal("1000"), List.of(new QuoteFacts.Line("+Item", 1, 1000, 1000)), false);
        assertThat(QuoteText.csv(quote)).contains("1,'=cmd,,'@note").contains("'+Item,1,1000,1000");
    }

    @Test
    void theFileIsNamedAfterTheProjectAndTheQuote() {
        assertThat(QuoteText.fileName("Ishtar ×5 for Kestrel Dawn!", 1)).isEqualTo("quote-ishtar-5-for-kestrel-dawn-1.csv");
        assertThat(QuoteText.fileName("***", 2)).isEqualTo("quote-project-2.csv");
        var long40 = QuoteText.fileName("a".repeat(30) + " " + "b".repeat(30), 7);
        assertThat(long40).startsWith("quote-").endsWith("-7.csv");
        assertThat(long40.length()).isLessThanOrEqualTo("quote-".length() + 40 + "-7.csv".length());
        assertThat(long40).matches("[a-z0-9.-]+");
    }
}
