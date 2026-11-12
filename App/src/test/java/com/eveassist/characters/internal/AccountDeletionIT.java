package com.eveassist.characters.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eveassist.IntegrationTest;
import com.eveassist.identity.internal.AccountDeletion;
import com.eveassist.identity.internal.KeycloakAdmin;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Deleting an account: what goes, what stays, and what EVE and Keycloak are told. */
@Import(TestCharactersConfig.class)
class AccountDeletionIT extends IntegrationTest {

    @Autowired
    AccountDeletion deletion;

    @Autowired
    CharacterRepository characters;

    @Autowired
    TokenCipher cipher;

    @Autowired
    FakeEveSso sso;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    KeycloakAdmin keycloak;

    @BeforeEach
    void reset() {
        sso.reset();
        when(keycloak.deleteUser(anyString())).thenReturn(true);
    }

    private long linkCharacter(String sub, String refreshToken) {
        var eveId = 2_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000);
        characters.link(viewerFor(sub).accountId(), eveId, "Pilot " + eveId, "owner-hash-" + eveId,
                Set.of("esi-assets.read_assets.v1"), cipher.encrypt(eveId, refreshToken));
        return eveId;
    }

    private void addPlan(String sub, String name) {
        jdbc.sql("INSERT INTO plan (public_id, owner_account_id, name) VALUES (:id, :owner, :name)")
                .param("id", UUID.randomUUID()).param("owner", viewerFor(sub).accountId().value())
                .param("name", name).update();
    }

    private int count(String table, String sub) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE owner_account_id = "
                + "(SELECT public_id FROM account WHERE keycloak_sub = :sub)").param("sub", sub)
                .query(Integer.class).single();
    }

    private int accounts(String sub) {
        return jdbc.sql("SELECT count(*) FROM account WHERE keycloak_sub = :sub").param("sub", sub)
                .query(Integer.class).single();
    }

    @Test
    void deletesTheAccountAndEverythingOfItsOwn() {
        var sub = newSub();
        linkCharacter(sub, "refresh-a");
        linkCharacter(sub, "refresh-b");
        addPlan(sub, "Hulls");
        var tokensBefore = jdbc.sql("SELECT count(*) FROM character_token").query(Integer.class).single();

        var result = deletion.delete(sub);

        assertThat(result).isEqualTo(AccountDeletion.Result.DELETED);
        assertThat(accounts(sub)).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM plan WHERE name = 'Hulls'").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM character_token").query(Integer.class).single())
                .isEqualTo(tokensBefore - 2);
    }

    @Test
    void forgetsWhatEsiSaidAboutEveryCharacterOfTheAccount() {
        var sub = newSub();
        var first = linkCharacter(sub, "refresh-a");
        var second = linkCharacter(sub, "refresh-b");
        var other = linkCharacter(newSub(), "refresh-other");

        deletion.delete(sub);

        // the stored ETags go with the account, so a character linked again later is not told "unchanged"; another account's stay
        assertThat(esi.forgottenCharacters()).containsExactlyInAnyOrder(first, second).doesNotContain(other);
    }

    @Test
    void revokesEveryTokenAtEveBeforeTheyAreGone() {
        var sub = newSub();
        linkCharacter(sub, "refresh-a");
        linkCharacter(sub, "refresh-b");

        deletion.delete(sub);

        assertThat(sso.revoked).containsExactlyInAnyOrder("refresh-a", "refresh-b");
    }

    @Test
    void goesOnWhenEveDoesNotConfirmARevocation() {
        var sub = newSub();
        linkCharacter(sub, "refresh-a");
        sso.revokeConfirmed = false;

        var result = deletion.delete(sub);

        assertThat(result).isEqualTo(AccountDeletion.Result.DELETED);
        assertThat(accounts(sub)).isZero();
    }

    @Test
    void leavesEveryOtherAccountAlone() {
        var mine = newSub();
        var theirs = newSub();
        linkCharacter(mine, "mine");
        linkCharacter(theirs, "theirs");
        addPlan(theirs, "Theirs");

        deletion.delete(mine);

        assertThat(accounts(theirs)).isEqualTo(1);
        assertThat(count("character", theirs)).isEqualTo(1);
        assertThat(count("plan", theirs)).isEqualTo(1);
        assertThat(sso.revoked).containsExactly("mine");
    }

    @Test
    void deletesTheKeycloakUserAndSignsTheUserOutEverywhere() {
        var sub = newSub();
        viewerFor(sub);
        jdbc.sql("INSERT INTO spring_session VALUES ('p-1', 's-1', 0, 0, 1800, 9999999999999, :sub)")
                .param("sub", sub).update();

        deletion.delete(sub);

        verify(keycloak).deleteUser(sub);
        assertThat(jdbc.sql("SELECT count(*) FROM spring_session WHERE principal_name = :sub").param("sub", sub)
                .query(Integer.class).single()).isZero();
    }

    @Test
    void saysSoWhenKeycloakCannotBeAskedAndTheDataIsGoneAnyway() {
        var sub = newSub();
        linkCharacter(sub, "refresh-a");
        when(keycloak.deleteUser(anyString())).thenReturn(false);

        var result = deletion.delete(sub);

        assertThat(result).isEqualTo(AccountDeletion.Result.DELETED_BUT_SIGN_IN_RECORD_REMAINS);
        assertThat(accounts(sub)).isZero();
    }

    @Test
    void deletingAgainFinishesWhatAnEarlierTryLeft() {
        var sub = newSub();
        when(keycloak.deleteUser(anyString())).thenReturn(false);
        deletion.delete(sub); // no account yet: only Keycloak is asked, and it fails
        viewerFor(sub);       // the user signs in again: an empty account
        when(keycloak.deleteUser(anyString())).thenReturn(true);

        var result = deletion.delete(sub);

        assertThat(result).isEqualTo(AccountDeletion.Result.DELETED);
        assertThat(accounts(sub)).isZero();
    }
}
