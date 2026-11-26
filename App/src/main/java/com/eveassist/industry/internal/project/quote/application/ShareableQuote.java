package com.eveassist.industry.internal.project.quote.application;

/**
 * A quote as it is sent to a buyer: the text to paste into a contract description or a message, the same as a CSV, and a name for that file.
 * {@code evenly} says that the lines are priced alike because a product had no market price to share the total by.
 */
public record ShareableQuote(String text, String csv, String fileName, boolean evenly) {
}
