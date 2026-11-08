package com.eveassist;

import com.eveassist.identity.Viewer;
import com.eveassist.identity.internal.AccountService;
import com.eveassist.sde.TypeId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The whole application, on Postgres set up as in production, with the small SDE in sde-fixture.sql. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestEsiConfig.class)
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
    protected TestEsi esi;

    @BeforeEach
    void freshEsi() {
        esi.reset();
    }

    protected String newSub() {
        return UUID.randomUUID().toString();
    }

    protected Viewer viewerFor(String sub) {
        return new Viewer(accounts.signedIn(sub, "Test Pilot").id());
    }
}
