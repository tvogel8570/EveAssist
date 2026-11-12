package com.eveassist.characters.internal;

import com.eveassist.characters.Feature;
import com.eveassist.identity.AccountId;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class FeatureRepository {

    private final JdbcClient jdbc;

    FeatureRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Set<Feature> turnedOn(AccountId owner) {
        var result = EnumSet.noneOf(Feature.class);
        jdbc.sql("SELECT feature FROM account_feature WHERE owner_account_id = :owner")
                .param("owner", owner.value())
                .query(String.class)
                .list()
                .forEach(name -> {
                    try {
                        result.add(Feature.valueOf(name));
                    } catch (IllegalArgumentException retired) {
                        // a feature that no longer exists asks for nothing
                    }
                });
        return result;
    }

    void turnOn(AccountId owner, Feature feature) {
        jdbc.sql("""
                INSERT INTO account_feature (owner_account_id, feature) VALUES (:owner, :feature)
                ON CONFLICT DO NOTHING
                """)
                .param("owner", owner.value()).param("feature", feature.name())
                .update();
    }

    void turnOff(AccountId owner, Feature feature) {
        jdbc.sql("DELETE FROM account_feature WHERE owner_account_id = :owner AND feature = :feature")
                .param("owner", owner.value()).param("feature", feature.name())
                .update();
    }
}
