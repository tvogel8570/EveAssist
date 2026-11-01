package com.eveassist.identity.internal;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;

    AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /** A Keycloak user EveAssist has not seen yet gets a new account. Accounts are never matched in any other way. */
    @Transactional
    public AccountDetails signedIn(String keycloakSub, String displayName) {
        accounts.insertIfAbsent(keycloakSub, displayName);
        return accounts.findByKeycloakSub(keycloakSub).orElseThrow();
    }

    public Optional<AccountDetails> findByKeycloakSub(String keycloakSub) {
        return accounts.findByKeycloakSub(keycloakSub);
    }
}
