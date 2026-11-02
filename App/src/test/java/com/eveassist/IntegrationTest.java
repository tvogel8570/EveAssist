package com.eveassist;

import com.eveassist.identity.Viewer;
import com.eveassist.identity.internal.AccountService;
import com.eveassist.market.JitaPrice;
import com.eveassist.market.MarketService;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The whole application, on Postgres set up as in production, with the small SDE in sde-fixture.sql. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTest {

    public static final TypeId HAULER = new TypeId(1001);
    public static final TypeId SECOND_SHIP = new TypeId(1003);
    public static final TypeId COMPONENT = new TypeId(1002);
    public static final TypeId TRITANIUM = new TypeId(34);
    public static final TypeId PILOTS_MINERAL = new TypeId(35);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Autowired
    protected AccountService accounts;

    @Autowired
    protected MarketService market;

    @BeforeEach
    void prices() {
        market.store(List.of(
                new JitaPrice(TRITANIUM, new BigDecimal("5.00"), Instant.parse("2020-01-01T00:00:00Z")),
                new JitaPrice(PILOTS_MINERAL, new BigDecimal("10.00"), Instant.parse("2020-01-01T00:00:00Z"))));
    }

    protected String newSub() {
        return UUID.randomUUID().toString();
    }

    /** Parts have no price unless a test gives them one, and prices are shared by all tests. */
    @BeforeEach
    void noPartPrices(@Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc) {
        jdbc.sql("DELETE FROM jita_price WHERE type_id = :part").param("part", COMPONENT.value()).update();
    }

    protected Viewer viewerFor(String sub) {
        return new Viewer(accounts.signedIn(sub, "Test Pilot").id());
    }
}
