package com.eveassist.industry.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.IntegrationTest;
import com.eveassist.identity.Viewer;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.domain.CostLine.Component;
import com.eveassist.industry.internal.domain.PriceSide;
import com.eveassist.industry.internal.domain.ProjectLockedException;
import com.eveassist.industry.internal.domain.ProjectWarning;
import com.eveassist.industry.internal.domain.ProjectWarning.Kind;
import com.eveassist.market.AdjustedPriceService;
import com.eveassist.market.JitaQuote;
import com.eveassist.sde.TypeId;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A price the user types for an item of a project, on the real tables, with the scenario of {@link TwoHaulers}: its Tritanium costs 5 sell and 4 buy and its
 * Pilot's Mineral 10 and 8, so the same demand costs 1,255 at sell and 1,004 at buy.
 */
class TypedPricesIT extends IntegrationTest {

    @Autowired
    ProjectEditing editing;

    @Autowired
    ProjectEntry entry;

    @Autowired
    ProjectCostings costings;

    @Autowired
    ProjectQueries queries;

    @Autowired
    ProjectQuoting quoting;

    @Autowired
    ProjectRepository projects;

    @Autowired
    AdjustedPriceService adjusted;

    @Autowired
    JdbcClient jdbc;

    private TwoHaulers haulers;

    @BeforeEach
    void adjustedPricesAndScenario() {
        haulers = new TwoHaulers(editing, projects, jdbc);
        esi.adjustedPrice(TRITANIUM.value(), "4");
        esi.adjustedPrice(PILOTS_MINERAL.value(), "8");
        esi.adjustedPrice(COMPONENT.value(), "100");
        adjusted.refresh();
    }

    private ProjectId project(Viewer owner) {
        return haulers.project(owner, haulers.azbel(owner, "2"), "Two Haulers", null);
    }

    private long versionOf(Viewer owner, ProjectId id) {
        return projects.find(owner, id).orElseThrow().version();
    }

    private ProjectChanged type(Viewer owner, ProjectId id, TypeId type, String price) {
        return editing.typePrice(owner, new TypePriceCommand(id, type, new BigDecimal(price), versionOf(owner, id)));
    }

    private Map<String, Object> row(ProjectId id, TypeId type) {
        return jdbc.sql("""
                SELECT p.source, p.sell, p.buy FROM project_price p JOIN project j ON j.id = p.project_id
                WHERE j.public_id = :id AND p.type_id = :type
                """).param("id", id.value()).param("type", type.value()).query().singleRow();
    }

    @Test
    void aTypedPriceIsStoredAsTypedOnBothSidesAndMovesTheVersion() {
        var owner = viewerFor(newSub());
        var id = project(owner);
        var before = versionOf(owner, id);
        assertThat(row(id, TRITANIUM).get("source")).isEqualTo("market");

        var changed = type(owner, id, TRITANIUM, "7.5");

        assertThat(changed.version()).isEqualTo(before + 1);
        var stored = row(id, TRITANIUM);
        assertThat(stored.get("source")).isEqualTo("manual");
        assertThat((BigDecimal) stored.get("sell")).isEqualByComparingTo("7.50");
        assertThat((BigDecimal) stored.get("buy")).isEqualByComparingTo("7.50");
        assertThat(projects.find(owner, id).orElseThrow().typedPrices()).containsExactly(TRITANIUM);
    }

    @Test
    void theCostingUsesATypedPriceUnderEveryPriceBasisInsteadOfTheMarketsSides() {
        var owner = viewerFor(newSub());
        var id = project(owner);
        // typed at the sell prices, so under the buy basis, which would cost 1,004, the typed prices cost what the sell prices do
        type(owner, id, TRITANIUM, "5");
        type(owner, id, PILOTS_MINERAL, "10");
        var project = projects.find(owner, id).orElseThrow();
        editing.setConfig(owner, new SetProjectConfigCommand(id, project.config().withPriceSide(PriceSide.BUY), project.version()));
        assertThat(costings.cost(owner, id).cost().amount(Component.MATERIALS)).isEqualByComparingTo("1255");

        project = projects.find(owner, id).orElseThrow();
        editing.setConfig(owner, new SetProjectConfigCommand(id, project.config().withPriceSide(PriceSide.SPLIT), project.version()));
        assertThat(costings.cost(owner, id).cost().amount(Component.MATERIALS)).isEqualByComparingTo("1255");
    }

    @Test
    void aRefreshOfTheMarketPricesLeavesATypedPriceAndChangesTheOthers() {
        var owner = viewerFor(newSub());
        var id = project(owner);
        type(owner, id, TRITANIUM, "7.5");

        editing.replacePrices(owner, id, versionOf(owner, id), Map.of(
                TRITANIUM, new JitaQuote(TRITANIUM, new BigDecimal("6"), new BigDecimal("5"), Instant.now()),
                PILOTS_MINERAL, new JitaQuote(PILOTS_MINERAL, new BigDecimal("11"), new BigDecimal("9"), Instant.now())));

        assertThat((BigDecimal) row(id, TRITANIUM).get("sell")).isEqualByComparingTo("7.50");
        assertThat(row(id, TRITANIUM).get("source")).isEqualTo("manual");
        assertThat((BigDecimal) row(id, PILOTS_MINERAL).get("sell")).isEqualByComparingTo("11");
        assertThat(row(id, PILOTS_MINERAL).get("source")).isEqualTo("market");
    }

    @Test
    void givingUpATypedPriceTakesTheMarketsFromEsiAndNoneWhenEsiHasNone() {
        var owner = viewerFor(newSub());
        var id = project(owner);
        type(owner, id, TRITANIUM, "7.5");
        type(owner, id, PILOTS_MINERAL, "12");
        esi.sell(TRITANIUM.value(), "6.00");
        // ESI cannot give a price for the Pilot's Mineral
        esi.fail(PILOTS_MINERAL.value());

        entry.useMarketPrice(owner, new UseMarketPriceCommand(id, TRITANIUM, versionOf(owner, id)));
        entry.useMarketPrice(owner, new UseMarketPriceCommand(id, PILOTS_MINERAL, versionOf(owner, id)));

        var stored = row(id, TRITANIUM);
        assertThat(stored.get("source")).isEqualTo("market");
        assertThat((BigDecimal) stored.get("sell")).isEqualByComparingTo("6.00");
        // ESI has no price for the Pilot's Mineral, so it has none, and the costing says so
        assertThat(projects.find(owner, id).orElseThrow().prices()).doesNotContainKey(PILOTS_MINERAL);
        assertThat(projects.find(owner, id).orElseThrow().typedPrices()).isEmpty();
        assertThat(costings.cost(owner, id).warnings()).contains(new ProjectWarning(Kind.UNPRICED_MATERIAL, PILOTS_MINERAL));
    }

    @Test
    void aTypedPriceMakesAnUnpricedMaterialPricedAndTheCostingQuotableAgain() {
        var owner = viewerFor(newSub());
        var id = project(owner);
        editing.useMarketPrice(owner, new UseMarketPriceCommand(id, PILOTS_MINERAL, versionOf(owner, id)), Optional.empty());
        var blocked = costings.cost(owner, id);
        assertThat(blocked.warnings()).contains(new ProjectWarning(Kind.UNPRICED_MATERIAL, PILOTS_MINERAL));
        assertThat(blocked.quotable()).isFalse();

        type(owner, id, PILOTS_MINERAL, "10");

        var result = costings.cost(owner, id);
        assertThat(result.warnings()).noneMatch(warning -> warning.kind() == Kind.UNPRICED_MATERIAL);
        assertThat(result.quotable()).isTrue();
    }

    @Test
    void aTypedPriceMakesTheCostingOutOfDateAndIsFrozenInAQuote() {
        var owner = viewerFor(newSub());
        var id = project(owner);
        entry.cost(owner, id);
        assertThat(queries.details(owner, id).costing().outOfDate()).isEmpty();

        type(owner, id, TRITANIUM, "7.5");
        assertThat(queries.details(owner, id).costing().outOfDate()).contains(CostingStaleness.PROJECT_EDITED);

        entry.cost(owner, id);
        quoting.issue(owner, new IssueQuoteCommand(id, versionOf(owner, id), "Buyer", null, Instant.now().plus(Duration.ofDays(7))));
        // the quote's inputs hold the typed price, and typing is refused on a quoted project
        assertThatThrownBy(() -> type(owner, id, TRITANIUM, "9")).isInstanceOf(ProjectLockedException.class);
    }

    @Test
    void anItemTheProjectDoesNotPriceAStaleVersionAndAnotherAccountsProjectAreRefused() {
        var owner = viewerFor(newSub());
        var id = project(owner);

        assertThatThrownBy(() -> type(owner, id, new TypeId(999_999), "5")).isInstanceOf(PriceNotNeededException.class);
        assertThatThrownBy(() -> editing.typePrice(owner, new TypePriceCommand(id, TRITANIUM, new BigDecimal("5"), versionOf(owner, id) - 1)))
                .isInstanceOf(ProjectChangedException.class);
        var version = versionOf(owner, id);
        assertThatThrownBy(() -> editing.typePrice(viewerFor(newSub()), new TypePriceCommand(id, TRITANIUM, new BigDecimal("5"), version)))
                .isInstanceOf(ProjectNotFoundException.class);
        assertThat(projects.find(owner, id).orElseThrow().typedPrices()).isEmpty();
    }
}
