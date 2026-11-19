package com.eveassist.industry.internal.application;

import com.eveassist.industry.ProjectId;
import java.time.Instant;
import org.jmolecules.architecture.cqrs.annotation.Command;

/**
 * Issues a quote from the project as the caller saw it. A buyer or a note left out is the one the Draft has, and a blank one is none.
 *
 * @param expectedVersion the project's version the caller saw, which must also be the version of the stored costing
 * @param expiresAt when the quote stops being open, or null for no expiry. It must be in the future
 */
@Command
public record IssueQuoteCommand(ProjectId project, long expectedVersion, String buyerName, String note, Instant expiresAt) {

    public static final int MAX_BUYER_LENGTH = 100;
    public static final int MAX_NOTE_LENGTH = 1_000;

    public IssueQuoteCommand {
        buyerName = clean(buyerName, MAX_BUYER_LENGTH, "buyer");
        note = clean(note, MAX_NOTE_LENGTH, "note");
    }

    /** Stripped, and null when there is nothing left. Too long is refused, whatever the page allowed. */
    private static String clean(String text, int max, String what) {
        if (text == null) {
            return null;
        }
        var stripped = text.strip();
        if (stripped.length() > max) {
            throw new InvalidQuoteException("The " + what + " is longer than " + max + " characters");
        }
        return stripped.isEmpty() ? null : stripped;
    }
}
