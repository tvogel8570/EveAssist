package com.eveassist.characters.internal;

import com.eveassist.identity.AccountDeletionHook;
import com.eveassist.identity.AccountId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Before an account is deleted, ends each of its characters' refresh tokens at EVE. Deleting our copy alone would leave the
 * token valid for as long as EVE keeps it, which is what removing a single character already avoids. When EVE does not
 * answer for one, the deletion goes on, because the user asked for their data to go; the token is then ours no more, and
 * the user can revoke it on CCP's third-party applications page, which the deletion page says.
 */
@Component
class CharactersAccountDeletion implements AccountDeletionHook {

    private static final Logger log = LoggerFactory.getLogger(CharactersAccountDeletion.class);

    private final CharacterRepository characters;
    private final TokenRevoker revoker;
    private final CharacterAccessTokens accessTokens;
    private final com.eveassist.esi.EsiClient esi;

    CharactersAccountDeletion(CharacterRepository characters, TokenRevoker revoker, CharacterAccessTokens accessTokens,
            com.eveassist.esi.EsiClient esi) {
        this.characters = characters;
        this.revoker = revoker;
        this.accessTokens = accessTokens;
        this.esi = esi;
    }

    @Override
    public void beforeDelete(AccountId account) {
        // the characters' rows go with the account; what ESI said about them (an ETag and an expiry each) must not stay behind
        characters.eveIdsOf(account).forEach(esi::forgetCharacter);
        for (var token : characters.tokensOf(account)) {
            accessTokens.forget(token.eveCharacterId());
            if (!revoker.revoke(token.eveCharacterId(), token.token())) {
                log.warn("A token of character {} was not revoked at EVE while its account was deleted", token.eveCharacterId());
            }
        }
    }
}
