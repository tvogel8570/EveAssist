package com.eveassist.identity;

/**
 * Something another module must do before an account's rows are deleted, while the data it needs is still there. The rows
 * themselves go with the account (every table of the account's data is removed with it by the database), so a hook is
 * for what the database cannot undo: the characters module revokes each stored token at EVE, for example.
 *
 * <p>{@code identity} allows no dependencies, so it cannot call the modules that hold the data; they implement this
 * instead, the same inversion as {@code esi}'s {@code AccessTokens}. A hook that throws stops the deletion before
 * anything is deleted, so the user can try again. A hook that can only partly succeed (EVE not answering) reports that to
 * the user itself and returns.
 */
@org.jmolecules.architecture.hexagonal.SecondaryPort
public interface AccountDeletionHook {

    void beforeDelete(AccountId account);
}
