package com.eveassist.esi.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.eveassist.esi.AccessTokens;
import com.eveassist.esi.EsiResult;
import com.eveassist.esi.StructureLookup;
import com.eveassist.esi.TokenGrant;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestClient;

/** The routes that need a character's token: the bearer header, the 401 retry and the calls that must not be made. */
class HttpEsiClientAssetsTest {

    private static final String PAGE_1 = "https://esi.test/characters/90000001/assets?page=1";
    private static final String ASSETS = """
            [{"is_singleton":false,"item_id":1000000000001,"location_flag":"Hangar","location_id":60003760,
              "location_type":"station","quantity":1500,"type_id":34},
             {"is_singleton":true,"item_id":1000000000002,"location_flag":"Hangar","location_id":1034323745897,
              "location_type":"other","quantity":1,"type_id":587},
             {"is_blueprint_copy":true,"is_singleton":true,"item_id":1000000000003,"location_flag":"Unlocked",
              "location_id":1000000000002,"location_type":"item","quantity":1,"type_id":1000}]
            """;

    /** Hands out numbered tokens, or a problem, and records what it was asked. */
    private static class FakeTokens implements AccessTokens {
        TokenGrant.Problem problem;
        int issued;
        final List<String> invalidated = new ArrayList<>();
        final List<String> scopesAsked = new ArrayList<>();

        @Override
        public TokenGrant accessToken(long eveCharacterId, String requiredScope) {
            scopesAsked.add(requiredScope);
            if (problem != null) {
                return TokenGrant.unavailable(problem);
            }
            return TokenGrant.of("token-" + (++issued));
        }

        @Override
        public void invalidate(long eveCharacterId) {
            invalidated.add("character " + eveCharacterId);
        }
    }

    private final TestClock clock = new TestClock(Instant.parse("2026-11-08T21:00:00Z"));
    private final Map<String, EsiCache.Entry> stored = new HashMap<>();
    private final EsiCache cache = new EsiCache() {
        @Override
        public Optional<Entry> find(String key) {
            return Optional.ofNullable(stored.get(key));
        }

        @Override
        public void save(String key, Entry entry, Instant fetchedAt) {
            stored.put(key, entry);
        }
    };
    private final FakeTokens tokens = new FakeTokens();
    private MockRestServiceServer esi;
    private HttpEsiClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        esi = MockRestServiceServer.bindTo(builder).build();
        var properties = new EsiProperties("https://esi.test", "dev@example.com", "2026-08-18", 8);
        client = new HttpEsiClient(builder, properties, cache, new EsiLimiter(clock, millis -> { }), clock, tokens);
    }

    private static DefaultResponseCreator page(String body, String etag, String expires) {
        return withSuccess(body, MediaType.APPLICATION_JSON).header("ETag", etag).header("Expires", expires)
                .header("Last-Modified", "Sun, 08 Nov 2026 21:00:00 GMT");
    }

    @Test
    void readsAssetsWithTheCharactersTokenAndMapsEveryField() {
        esi.expect(requestTo(PAGE_1)).andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(page(ASSETS, "\"a1\"", "Sun, 08 Nov 2026 21:05:00 GMT"));

        var result = client.characterAssets(90000001L);

        assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.UPDATED);
        assertThat(result.data()).hasSize(3);
        var stack = result.data().getFirst();
        assertThat(stack.itemId()).isEqualTo(1000000000001L);
        assertThat(stack.typeId()).isEqualTo(34);
        assertThat(stack.quantity()).isEqualTo(1500);
        assertThat(stack.locationId()).isEqualTo(60003760L);
        assertThat(stack.locationType()).isEqualTo("station");
        assertThat(stack.locationFlag()).isEqualTo("Hangar");
        assertThat(stack.singleton()).isFalse();
        assertThat(stack.blueprintCopy()).isNull();
        assertThat(result.data().get(1).locationType()).isEqualTo("other");
        assertThat(result.data().get(2).blueprintCopy()).isTrue();
        assertThat(result.data().get(2).locationId()).isEqualTo(1000000000002L);
        assertThat(tokens.scopesAsked).containsExactly("esi-assets.read_assets.v1");
        esi.verify();
    }

    @Test
    void doesNotCallAgainBeforeTheCopyExpiresAndTakesA304AsUnchanged() {
        esi.expect(requestTo(PAGE_1)).andRespond(page(ASSETS, "\"a1\"", "Sun, 08 Nov 2026 21:05:00 GMT"));
        client.characterAssets(90000001L);

        clock.advance(Duration.ofMinutes(4));
        assertThat(client.characterAssets(90000001L).outcome()).isEqualTo(EsiResult.Outcome.UNCHANGED);

        clock.advance(Duration.ofMinutes(2));
        esi.verify();
        esi.reset();
        esi.expect(requestTo(PAGE_1)).andExpect(header("If-None-Match", "\"a1\""))
                .andRespond(withStatus(HttpStatus.NOT_MODIFIED).header("Expires", "Sun, 08 Nov 2026 21:11:00 GMT"));

        assertThat(client.characterAssets(90000001L).outcome()).isEqualTo(EsiResult.Outcome.UNCHANGED);
        esi.verify();
    }

    @Test
    void keepsEachCharactersCopyApart() {
        esi.expect(requestTo(PAGE_1)).andRespond(page(ASSETS, "\"a1\"", "Sun, 08 Nov 2026 21:05:00 GMT"));
        esi.expect(requestTo("https://esi.test/characters/90000002/assets?page=1"))
                .andRespond(page("[]", "\"b1\"", "Sun, 08 Nov 2026 21:05:00 GMT"));
        client.characterAssets(90000001L);

        var other = client.characterAssets(90000002L);

        assertThat(other.outcome()).isEqualTo(EsiResult.Outcome.UPDATED);
        assertThat(other.data()).isEmpty();
        esi.verify();
    }

    @Test
    void forgetsARefusedTokenAndAsksOnceMoreWithANewOne() {
        esi.expect(requestTo(PAGE_1)).andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        esi.expect(requestTo(PAGE_1)).andExpect(header("Authorization", "Bearer token-2"))
                .andRespond(page(ASSETS, "\"a1\"", "Sun, 08 Nov 2026 21:05:00 GMT"));

        var result = client.characterAssets(90000001L);

        assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.UPDATED);
        assertThat(tokens.invalidated).containsExactly("character 90000001");
        esi.verify();
    }

    @Test
    void stopsAfterOneRetryWhenTheNewTokenIsRefusedToo() {
        esi.expect(requestTo(PAGE_1)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        esi.expect(requestTo(PAGE_1)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        var result = client.characterAssets(90000001L);

        assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.FAILED);
        assertThat(result.problem()).contains("401").doesNotContain("token-");
        esi.verify();
    }

    @Test
    void doesNotCallEsiForACharacterWithoutTheScopeOrNeedingAuthorization() {
        for (var problem : TokenGrant.Problem.values()) {
            tokens.problem = problem;

            var result = client.characterAssets(90000001L);

            assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.FAILED);
            assertThat(result.problem()).isNotBlank();
        }
        // the mock server was given no expectation, so any request would have failed the test
        esi.verify();
    }

    @Test
    void readsAStructureNameWithTheCharactersTokenAndTheStructuresScope() {
        esi.expect(requestTo("https://esi.test/universe/structures/1023603822720"))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withSuccess("""
                        {"name":"Jita - Trade Hub","owner_id":98000001,"position":{"x":1,"y":2,"z":3},
                         "solar_system_id":30000142,"type_id":35834}""", MediaType.APPLICATION_JSON));

        var answer = client.structure(90000001L, 1023603822720L);

        assertThat(answer.status()).isEqualTo(StructureLookup.Status.FOUND);
        assertThat(answer.name()).isEqualTo("Jita - Trade Hub");
        assertThat(answer.solarSystemId()).isEqualTo(30000142);
        assertThat(tokens.scopesAsked).containsExactly("esi-universe.read_structures.v1");
        esi.verify();
    }

    @Test
    void aStructureTheCharacterMayNotDockAtIsAnAnswerNotAFailure() {
        esi.expect(requestTo("https://esi.test/universe/structures/1")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        esi.expect(requestTo("https://esi.test/universe/structures/2")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.structure(90000001L, 1L).status()).isEqualTo(StructureLookup.Status.NO_ACCESS);
        assertThat(client.structure(90000001L, 2L).status()).isEqualTo(StructureLookup.Status.NOT_FOUND);
        esi.verify();
    }

    @Test
    void aStructureAskedAfterA401UsesANewTokenOnceAndNeverLoops() {
        esi.expect(requestTo("https://esi.test/universe/structures/1")).andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        esi.expect(requestTo("https://esi.test/universe/structures/1")).andExpect(header("Authorization", "Bearer token-2"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        var answer = client.structure(90000001L, 1L);

        assertThat(answer.status()).isEqualTo(StructureLookup.Status.FAILED);
        assertThat(tokens.invalidated).hasSize(1);
        esi.verify();
    }

    @Test
    void aRefusalCarriesEsisOwnWordsSoItsCauseCanBeSeen() {
        esi.expect(requestTo("https://esi.test/universe/structures/1"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Forbidden\"}"));

        var answer = client.structure(90000001L, 1L);

        assertThat(answer.status()).isEqualTo(StructureLookup.Status.NO_ACCESS);
        assertThat(answer.problem()).contains("Forbidden").doesNotContain("token-");
    }

    @Test
    void aStructureServerErrorOrAMissingTokenIsAFailureWithAReason() {
        esi.expect(requestTo("https://esi.test/universe/structures/1")).andRespond(withServerError());
        var serverError = client.structure(90000001L, 1L);
        assertThat(serverError.status()).isEqualTo(StructureLookup.Status.FAILED);
        assertThat(serverError.problem()).isNotBlank();

        tokens.problem = TokenGrant.Problem.MISSING_SCOPE;
        var noScope = client.structure(90000001L, 3L);

        assertThat(noScope.status()).isEqualTo(StructureLookup.Status.FAILED);
        assertThat(noScope.problem()).contains("esi-universe.read_structures.v1");
    }

    @Test
    void publicRoutesCarryNoAuthorizationHeader() {
        esi.expect(requestTo("https://esi.test/markets/10000002/orders?type_id=34&page=1"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(page("[]", "\"o\"", "Sun, 08 Nov 2026 21:05:00 GMT"));

        client.marketOrders(10000002, 34);

        assertThat(tokens.scopesAsked).isEmpty();
        esi.verify();
    }

    // ---- corporations

    @Test
    void readsACorporationsAssetsWithTheDirectorsTokenAndTheCorporationScope() {
        esi.expect(requestTo("https://esi.test/corporations/98000001/assets?page=1"))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(page("""
                        [{"is_singleton":false,"item_id":1040000000001,"location_flag":"OfficeFolder",
                          "location_id":60003760,"location_type":"station","quantity":1,"type_id":27},
                         {"is_singleton":false,"item_id":1040000000002,"location_flag":"CorpSAG2",
                          "location_id":1040000000001,"location_type":"item","quantity":900,"type_id":34}]
                        """, "\"c1\"", "Sun, 08 Nov 2026 21:05:00 GMT"));

        var result = client.corporationAssets(98000001L, 90000001L);

        assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.UPDATED);
        assertThat(result.data()).hasSize(2);
        assertThat(result.data().get(1).locationFlag()).isEqualTo("CorpSAG2");
        assertThat(result.data().get(1).quantity()).isEqualTo(900);
        assertThat(tokens.scopesAsked).containsExactly("esi-assets.read_corporation_assets.v1");
        esi.verify();
    }

    @Test
    void aCorporationsAssetsAreCachedLikeACharactersAndAForbiddenDirectorIsAFailure() {
        esi.expect(requestTo("https://esi.test/corporations/98000001/assets?page=1"))
                .andRespond(page("[]", "\"c1\"", "Sun, 08 Nov 2026 21:05:00 GMT"));
        client.corporationAssets(98000001L, 90000001L);
        clock.advance(Duration.ofMinutes(1));

        assertThat(client.corporationAssets(98000001L, 90000001L).outcome()).isEqualTo(EsiResult.Outcome.UNCHANGED);

        clock.advance(Duration.ofMinutes(10));
        esi.reset();
        esi.expect(requestTo("https://esi.test/corporations/98000001/assets?page=1"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        var refused = client.corporationAssets(98000001L, 90000001L);

        assertThat(refused.outcome()).isEqualTo(EsiResult.Outcome.FAILED);
        assertThat(refused.problem()).contains("403");
        esi.verify();
    }

    @Test
    void readsTheHangarNamesWithTheDirectorsTokenAndTheDivisionsScope() {
        esi.expect(requestTo("https://esi.test/corporations/98000001/divisions"))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withSuccess("""
                        {"hangar":[{"division":1,"name":"Master Wallet"},{"division":2,"name":"Production"},
                                   {"division":3}],
                         "wallet":[{"division":1,"name":"Wallet"}]}""", MediaType.APPLICATION_JSON));

        var result = client.corporationHangars(98000001L, 90000001L);

        assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.UPDATED);
        assertThat(result.data()).containsOnly(Map.entry(1, "Master Wallet"), Map.entry(2, "Production"));
        assertThat(tokens.scopesAsked).containsExactly("esi-corporations.read_divisions.v1");
        esi.verify();
    }

    @Test
    void readsACharactersRolesWithItsTokenAndTheRolesScope() {
        esi.expect(requestTo("https://esi.test/characters/90000001/roles"))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withSuccess("""
                        {"roles":["Director","Factory_Manager"],"roles_at_base":["Director"]}""",
                        MediaType.APPLICATION_JSON));

        var result = client.characterRoles(90000001L);

        assertThat(result.data()).containsExactlyInAnyOrder("Director", "Factory_Manager");
        assertThat(tokens.scopesAsked).containsExactly("esi-characters.read_corporation_roles.v1");
        esi.verify();
    }

    @Test
    void aCharacterWithNoRolesGetsAnEmptySet() {
        esi.expect(requestTo("https://esi.test/characters/90000001/roles"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.characterRoles(90000001L).data()).isEmpty();
    }

    @Test
    void theCorporationOfACharacterAndTheNameOfACorporationArePublicAndCarryNoToken() {
        esi.expect(requestTo("https://esi.test/characters/90000001"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("""
                        {"name":"Alt One","corporation_id":98000001,"birthday":"2010-01-01T00:00:00Z"}""",
                        MediaType.APPLICATION_JSON));
        esi.expect(requestTo("https://esi.test/corporations/98000001"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("""
                        {"name":"Test Industries","ticker":"TEST","member_count":9}""", MediaType.APPLICATION_JSON));

        assertThat(client.characterCorporationId(90000001L).data()).isEqualTo(98000001L);
        assertThat(client.corporationName(98000001L).data()).isEqualTo("Test Industries");
        assertThat(tokens.scopesAsked).isEmpty();
        esi.verify();
    }

    @Test
    void aRolesCallForACharacterWithoutTheScopeIsNotMade() {
        tokens.problem = TokenGrant.Problem.MISSING_SCOPE;

        var result = client.characterRoles(90000001L);

        assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.FAILED);
        assertThat(result.problem()).contains("esi-characters.read_corporation_roles.v1");
        esi.verify();
    }

    @Test
    void aServerErrorOnAnObjectRouteIsAFailureWithAReason() {
        esi.expect(requestTo("https://esi.test/corporations/98000001")).andRespond(withServerError());

        var result = client.corporationName(98000001L);

        assertThat(result.outcome()).isEqualTo(EsiResult.Outcome.FAILED);
        assertThat(result.problem()).isNotBlank();
    }
}
