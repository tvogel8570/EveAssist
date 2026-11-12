package com.eveassist.identity;

import java.util.List;
import java.util.Map;

/**
 * What a module holds for an account, as named sections of plain rows. The account's JSON export is these sections written
 * out, and the page that asks for confirmation before a deletion counts their rows, so the user sees what will go and what
 * they can take with them from one list that cannot drift apart.
 *
 * <p>{@code identity} allows no dependencies, so each module that stores data for an account implements this (the same
 * inversion as {@link AccountDeletionHook}). Values are strings, numbers, booleans, lists and maps; times are ISO-8601 text.
 * A row never carries a token, a secret or anything another account's owner supplied.
 */
@org.jmolecules.architecture.hexagonal.SecondaryPort
public interface AccountDataSource {

    /** Section name to its rows, for the account only. A section with no rows may be left out or empty. */
    Map<String, List<Map<String, Object>>> sections(AccountId account);
}
