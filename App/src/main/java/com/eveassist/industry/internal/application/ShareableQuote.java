package com.eveassist.industry.internal.application;

/** A quote as it is sent to a buyer: the text to paste into a contract description or a message, the same as a CSV, and a name for that file. */
public record ShareableQuote(String text, String csv, String fileName) {
}
