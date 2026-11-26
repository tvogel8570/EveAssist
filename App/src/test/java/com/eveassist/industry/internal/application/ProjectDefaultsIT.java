package com.eveassist.industry.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.IntegrationTest;
import com.eveassist.identity.AccountDataSource;
import com.eveassist.identity.Viewer;
import com.eveassist.industry.FacilityId;
import com.eveassist.industry.PlanId;
import com.eveassist.industry.internal.domain.PriceSide;
import com.eveassist.industry.internal.domain.ProjectBuilder;
import com.eveassist.industry.internal.domain.ProjectConfig;
import com.eveassist.industry.internal.domain.ProjectSettings;
import com.eveassist.industry.internal.domain.ProjectSettings.QuoteMode;
import com.eveassist.industry.internal.costing.domain.SplitPolicy;
import com.eveassist.industry.internal.costing.domain.SplitStrategy;
import com.eveassist.industry.internal.facility.application.FacilityEditing;
import com.eveassist.industry.internal.facility.application.FacilityNotFoundException;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** An account's project defaults: what a new project starts from, what they leave alone, and that they go and are exported with the account. */
class ProjectDefaultsIT extends IntegrationTest {

    private static final TypeId NANITE = new TypeId(11442);

    @Autowired
    ProjectDefaults defaults;

    @Autowired
    ProjectEditing editing;

    @Autowired
    ProjectRepository projects;

    @Autowired
    PlanRepository plans;

    @Autowired
    FacilityEditing facilities;

    @Autowired
    List<AccountDataSource> accountData;

    @Autowired
    JdbcClient jdbc;

    private FacilityId facilityOf(Viewer viewer) {
        var id = UUID.randomUUID();
        jdbc.sql("INSERT INTO facility (public_id, owner_account_id, name) VALUES (:id, :owner, 'Azbel')")
                .param("id", id).param("owner", viewer.accountId().value()).update();
        return new FacilityId(id);
    }

    private PlanId planOf(Viewer owner) {
        return plans.create(owner, "Plan");
    }

    private ProjectConfig startedFrom(Viewer owner) {
        var started = editing.start(owner, new StartProjectCommand(planOf(owner), null));
        return projects.find(owner, started.project()).orElseThrow().config();
    }

    /** Every value differs from the built-in one, so that one that is not stored shows up. */
    private ProjectConfig chosen(FacilityId facility) {
        return new ProjectConfig(
                new ProjectSettings(new BigDecimal("500000"), new BigDecimal("300"), new BigDecimal("25000"), QuoteMode.MARKET,
                        new BigDecimal("12.5"), new BigDecimal("-5"), new BigDecimal("10000")),
                PriceSide.SPLIT, new SplitPolicy(SplitStrategy.COPIES_FIRST, 172_800L), facility,
                new ProjectBuilder(5, 4, new BigDecimal("4"), Map.of(NANITE, 3)), null, null);
    }

    private static void assertSame(ProjectConfig actual, ProjectConfig expected) {
        assertThat(actual).usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class).isEqualTo(expected);
    }

    @Test
    void anAccountThatSavedNothingHasTheBuiltInValuesAndSoDoesItsNewProject() {
        var owner = viewerFor(newSub());

        assertSame(defaults.of(owner), ProjectConfig.defaults());
        assertSame(startedFrom(owner), ProjectConfig.defaults());
    }

    @Test
    void aNewProjectStartsFromEveryValueTheAccountSaved() {
        var owner = viewerFor(newSub());
        var config = chosen(facilityOf(owner));

        defaults.save(owner, config);

        assertSame(defaults.of(owner), config);
        assertSame(startedFrom(owner), config);
    }

    @Test
    void aBuyerAndANoteAreNeverDefaults() {
        var owner = viewerFor(newSub());

        defaults.save(owner, chosen(null).withBuyer("A buyer", "by Friday"));

        assertThat(defaults.of(owner).buyerName()).isNull();
        assertThat(defaults.of(owner).note()).isNull();
        assertThat(startedFrom(owner).buyerName()).isNull();
    }

    @Test
    void savingAgainReplacesWhatWasSavedIncludingTheSkillsAndLeavesProjectsAsTheyAre() {
        var owner = viewerFor(newSub());
        defaults.save(owner, chosen(null));
        var earlier = editing.start(owner, new StartProjectCommand(planOf(owner), "Earlier"));

        var other = ProjectConfig.defaults().withBuilder(new ProjectBuilder(1, 1, BigDecimal.ZERO, Map.of(new TypeId(11452), 2)));
        defaults.save(owner, other);

        assertSame(defaults.of(owner), other);
        var kept = projects.find(owner, earlier.project()).orElseThrow();
        assertSame(kept.config(), chosen(null));
        assertThat(kept.version()).isEqualTo(earlier.version());
    }

    @Test
    void resettingReturnsToTheBuiltInValues() {
        var owner = viewerFor(newSub());
        defaults.save(owner, chosen(facilityOf(owner)));

        defaults.reset(owner);

        assertSame(defaults.of(owner), ProjectConfig.defaults());
        assertSame(startedFrom(owner), ProjectConfig.defaults());
    }

    @Test
    void defaultsBelongToOneAccountAndCannotNameAnothersFacility() {
        var owner = viewerFor(newSub());
        var other = viewerFor(newSub());
        defaults.save(owner, chosen(null));

        assertSame(defaults.of(other), ProjectConfig.defaults());
        assertThatThrownBy(() -> defaults.save(other, chosen(facilityOf(owner)))).isInstanceOf(FacilityNotFoundException.class);
        assertSame(defaults.of(other), ProjectConfig.defaults());
    }

    @Test
    void deletingTheDefaultFacilityLeavesTheOtherDefaults() {
        var owner = viewerFor(newSub());
        var facility = facilityOf(owner);
        defaults.save(owner, chosen(facility));

        facilities.delete(owner, facility);

        assertSame(defaults.of(owner), chosen(null));
        assertSame(startedFrom(owner), chosen(null));
    }

    @Test
    void theDefaultsAreInTheAccountsExportAndGoWithTheAccount() {
        var owner = viewerFor(newSub());
        var facility = facilityOf(owner);
        assertThat(section(owner)).isEmpty();
        defaults.save(owner, chosen(facility));

        var rows = section(owner);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("priceBasis", "split").containsEntry("defaultFacility", "Azbel").containsEntry("industry", 5);
            assertThat(row.get("skills").toString()).contains("11442").contains("Nanite Engineering").contains("level=3");
        });
        assertThat(section(viewerFor(newSub()))).isEmpty();

        jdbc.sql("DELETE FROM account WHERE public_id = :a").param("a", owner.accountId().value()).update();

        for (var table : new String[] {"project_default", "project_default_skill"}) {
            assertThat(jdbc.sql("SELECT count(*) FROM " + table + " WHERE owner_account_id = :a").param("a", owner.accountId().value())
                    .query(Integer.class).single()).as(table).isZero();
        }
    }

    private List<Map<String, Object>> section(Viewer viewer) {
        return accountData.stream().flatMap(source -> source.sections(viewer.accountId()).entrySet().stream())
                .filter(section -> section.getKey().equals("projectDefaults")).flatMap(section -> section.getValue().stream()).toList();
    }
}
