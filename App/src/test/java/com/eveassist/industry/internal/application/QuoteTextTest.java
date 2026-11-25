package com.eveassist.industry.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** The text and the CSV a buyer gets. They are worked out by hand from the format the developer chose on 2026-11-25. */
class QuoteTextTest {

    /** The page stores "valid until 2026-12-02" as the start of the next day. */
    private static final Instant EXPIRES = Instant.parse("2026-12-03T00:00:00Z");

    private static QuoteFacts ishtars() {
        return new QuoteFacts(1, "Kestrel Dawn", "Delivery to Perimeter.", EXPIRES, new BigDecimal("266053000.00"),
                List.of(new QuoteFacts.Item("Ishtar", 5)));
    }

    @Test
    void theTextIsTheOneTheDeveloperApprovedInThePreview() {
        assertThat(QuoteText.text(ishtars())).isEqualTo("""
                Quote 1 for Kestrel Dawn
                5 × Ishtar, delivered by contract
                Total: 266,053,000 ISK
                Per unit: 53,210,600 ISK
                Valid until 2026-12-02
                Delivery to Perimeter.""");
    }

    @Test
    void aQuoteWithNoBuyerNoExpiryAndNoNoteLeavesThoseLinesOut() {
        var quote = new QuoteFacts(3, null, null, null, new BigDecimal("1500000"), List.of(new QuoteFacts.Item("Vexor", 1)));

        assertThat(QuoteText.text(quote)).isEqualTo("""
                Quote 3
                1 × Vexor, delivered by contract
                Total: 1,500,000 ISK
                Per unit: 1,500,000 ISK""");
    }

    @Test
    void severalProductsAreListedAndHaveNoPriceForOneUnit() {
        var quote = new QuoteFacts(2, "Buyer", null, null, new BigDecimal("900000"),
                List.of(new QuoteFacts.Item("Ishtar", 5), new QuoteFacts.Item("Vexor", 2)));

        assertThat(QuoteText.text(quote)).isEqualTo("""
                Quote 2 for Buyer
                5 × Ishtar, 2 × Vexor, delivered by contract
                Total: 900,000 ISK""");
    }

    @Test
    void thePricePerUnitIsRoundedHalfUpToTheWholeIsk() {
        var third = new QuoteFacts(1, null, null, null, new BigDecimal("100000"), List.of(new QuoteFacts.Item("A", 3)));
        var half = new QuoteFacts(1, null, null, null, new BigDecimal("100001"), List.of(new QuoteFacts.Item("A", 2)));

        assertThat(QuoteText.text(third)).contains("Per unit: 33,333 ISK");
        assertThat(QuoteText.text(half)).contains("Per unit: 50,001 ISK");
    }

    @Test
    void moneyKeepsItsCommasWhateverTheServersLanguage() {
        var before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertThat(QuoteText.money(new BigDecimal("1234567.5"))).isEqualTo("1,234,568");
            assertThat(QuoteText.text(ishtars())).contains("Total: 266,053,000 ISK");
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    void theCsvHasARowForEachProductForThePricePerUnitAndForTheTotal() {
        assertThat(QuoteText.csv(ishtars())).isEqualTo(String.join("\r\n",
                "Quote,Buyer,Item,Quantity,Price (ISK),Valid until,Note",
                "1,Kestrel Dawn,Ishtar,5,,2026-12-02,Delivery to Perimeter.",
                "1,Kestrel Dawn,Per unit,,53210600,2026-12-02,Delivery to Perimeter.",
                "1,Kestrel Dawn,Total,,266053000,2026-12-02,Delivery to Perimeter.", ""));
    }

    @Test
    void aCsvOfSeveralProductsHasNoRowForThePricePerUnit() {
        var quote = new QuoteFacts(2, "Buyer", null, null, new BigDecimal("900000"),
                List.of(new QuoteFacts.Item("Ishtar", 5), new QuoteFacts.Item("Vexor", 2)));

        assertThat(QuoteText.csv(quote)).isEqualTo(String.join("\r\n",
                "Quote,Buyer,Item,Quantity,Price (ISK),Valid until,Note",
                "2,Buyer,Ishtar,5,,,",
                "2,Buyer,Vexor,2,,,",
                "2,Buyer,Total,,900000,,", ""));
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
    void aFieldThatAspreadsheetWouldReadAsAFormulaIsPutRightWithAQuote() {
        // the buyer's name and the note are text somebody typed
        assertThat(QuoteText.csvField("=1+1")).isEqualTo("'=1+1");
        assertThat(QuoteText.csvField("+1")).isEqualTo("'+1");
        assertThat(QuoteText.csvField("-2")).isEqualTo("'-2");
        assertThat(QuoteText.csvField("@sum")).isEqualTo("'@sum");
        assertThat(QuoteText.csvField("\t=x")).isEqualTo("'\t=x");
        assertThat(QuoteText.csvField("=HYPERLINK(\"http://x\",\"y\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\",\"\"y\"\")\"");
        // a field with an equals sign inside it is not a formula
        assertThat(QuoteText.csvField("a=b")).isEqualTo("a=b");

        var quote = new QuoteFacts(1, "=cmd", "@note", null, new BigDecimal("1000"), List.of(new QuoteFacts.Item("+Item", 1)));
        assertThat(QuoteText.csv(quote)).contains("1,'=cmd,'+Item,1,,,'@note");
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
