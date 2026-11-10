package com.eveassist.inventory.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InventoryControllerTest {

    @Test
    void aCsvLineQuotesEveryCellAndDoublesQuotes() {
        assertThat(InventoryController.csvLine("Tritanium", "a \"b\" c", "x,y")).isEqualTo(
                "\"Tritanium\",\"a \"\"b\"\" c\",\"x,y\"\r\n");
    }

    @Test
    void aCellThatWouldRunAsAFormulaGetsAQuoteInFront() {
        assertThat(InventoryController.csvLine("=HYPERLINK(\"http://x\")", "+1+1", "@SUM(A1)", "-cmd")).isEqualTo(
                "\"'=HYPERLINK(\"\"http://x\"\")\",\"'+1+1\",\"'@SUM(A1)\",\"'-cmd\"\r\n");
    }

    @Test
    void aPlainNegativeNumberIsLeftAlone() {
        assertThat(InventoryController.csvLine("-5", "12")).isEqualTo("\"-5\",\"12\"\r\n");
    }

    @Test
    void aMissingCellIsEmpty() {
        assertThat(InventoryController.csvLine("a", null, "")).isEqualTo("\"a\",\"\",\"\"\r\n");
    }
}
