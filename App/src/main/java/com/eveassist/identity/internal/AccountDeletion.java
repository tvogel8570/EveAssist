package com.eveassist.identity.internal;

import com.eveassist.identity.AccountDeletionHook;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deletes an account and everything of its own: the other modules' hooks first (while their data is there), then the
 * rows, the sessions and the Keycloak user. Deleting again is safe and finishes what an earlier try left: if Keycloak could
 * not be asked, the data is already gone and the user can sign in and delete the empty account that sign-in makes, which
 * deletes the Keycloak user too.
 */
@Service
public class AccountDeletion {

    public enum Result {
        /** The account's data and its Keycloak user are gone. */
        DELETED,
        /** The account's data is gone, but Keycloak was not reachable, so its sign-in record (name, no data) remains. */
        DELETED_BUT_SIGN_IN_RECORD_REMAINS
    }

    private final AccountRepository accounts;
    private final KeycloakAdmin keycloak;
    private final List<AccountDeletionHook> hooks;
    private final TransactionTemplate transaction;

    AccountDeletion(AccountRepository accounts, KeycloakAdmin keycloak, List<AccountDeletionHook> hooks,
            TransactionTemplate transaction) {
        this.accounts = accounts;
        this.keycloak = keycloak;
        this.hooks = hooks;
        this.transaction = transaction;
    }

    public Result delete(String keycloakSub) {
        accounts.findByKeycloakSub(keycloakSub).ifPresent(account -> {
            // outside the transaction: a hook calls EVE, and no database connection should wait on that
            hooks.forEach(hook -> hook.beforeDelete(account.id()));
            transaction.executeWithoutResult(status -> {
                accounts.delete(account.id());
                accounts.deleteSessions(keycloakSub);
            });
        });
        return keycloak.deleteUser(keycloakSub) ? Result.DELETED : Result.DELETED_BUT_SIGN_IN_RECORD_REMAINS;
    }
}
