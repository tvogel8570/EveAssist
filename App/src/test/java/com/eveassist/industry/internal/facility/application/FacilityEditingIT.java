package com.eveassist.industry.internal.facility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.IntegrationTest;
import com.eveassist.identity.Viewer;
import com.eveassist.industry.FacilityId;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.project.costing.application.CostingStaleness;
import com.eveassist.industry.internal.project.quote.application.IssueQuoteCommand;
import com.eveassist.industry.internal.project.application.ProjectEditing;
import com.eveassist.industry.internal.project.application.ProjectEntry;
import com.eveassist.industry.internal.project.application.ProjectQueries;
import com.eveassist.industry.internal.project.quote.application.ProjectQuoting;
import com.eveassist.industry.internal.project.application.ProjectRepository;
import com.eveassist.industry.internal.project.application.TwoHaulers;
import com.eveassist.industry.internal.costing.domain.FacilityTerms;
import com.eveassist.industry.internal.facility.domain.StructureType;
import com.eveassist.market.AdjustedPriceService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Adding, changing and deleting a facility, on the real tables, with the scenario of {@link TwoHaulers} for the projects that use one. Each
 * test makes its own accounts.
 */
class FacilityEditingIT extends IntegrationTest {

    @Autowired
    FacilityEditing editing;

    @Autowired
    FacilityQueries queries;

    @Autowired
    ProjectEditing projectEditing;

    @Autowired
    ProjectRepository projects;

    @Autowired
    ProjectEntry entry;

    @Autowired
    ProjectQueries projectQueries;

    @Autowired
    ProjectQuoting quoting;

    @Autowired
    AdjustedPriceService adjusted;

    @Autowired
    JdbcClient jdbc;

    private TwoHaulers haulers;

    @BeforeEach
    void adjustedPricesAndScenario() {
        haulers = new TwoHaulers(projectEditing, projects, jdbc);
        esi.adjustedPrice(TRITANIUM.value(), "4");
        esi.adjustedPrice(PILOTS_MINERAL.value(), "8");
        esi.adjustedPrice(COMPONENT.value(), "100");
        adjusted.refresh();
    }

    private static FacilityTerms terms(String tax, String index) {
        return new FacilityTerms(new BigDecimal("1"), BigDecimal.ZERO, new BigDecimal("20"), BigDecimal.ZERO, new BigDecimal("4"),
                new BigDecimal(index), new BigDecimal(tax), new BigDecimal("4"));
    }

    private static FacilityValues azbel(String name, String tax, String index) {
        return new FacilityValues(name, StructureType.AZBEL, terms(tax, index));
    }

    private long versionOf(Viewer owner, ProjectId id) {
        return projects.find(owner, id).orElseThrow().version();
    }

    private FacilityOverview facility(Viewer owner, FacilityId id) {
        return queries.find(owner, id);
    }

    @Test
    void anAddedFacilityIsListedWithItsValuesItsStructureAndWhenItsIndexWasTyped() {
        var owner = viewerFor(newSub());
        var before = Instant.now().minus(Duration.ofSeconds(5));

        var id = editing.add(owner, azbel("  Dodenvale Azbel ", "0", "2.16"));

        var listed = queries.list(owner);
        assertThat(listed).hasSize(1);
        var found = listed.getFirst().facility();
        assertThat(found.id()).isEqualTo(id);
        assertThat(found.name()).isEqualTo("Dodenvale Azbel");
        assertThat(found.structureType()).isEqualTo(StructureType.AZBEL);
        assertThat(found.terms().systemCostIndexPercent()).isEqualByComparingTo("2.16");
        assertThat(found.terms().structureTimePercent()).isEqualByComparingTo("20");
        assertThat(found.indexEnteredAt()).isAfter(before);
        assertThat(listed.getFirst().draftProjects()).isZero();
    }

    @Test
    void aFacilityWithNoStructureTypeIsAllowed() {
        var owner = viewerFor(newSub());

        var id = editing.add(owner, new FacilityValues("A Fortizar", null, terms("0", "1")));

        assertThat(facility(owner, id).structureType()).isNull();
    }

    @Test
    void savingAFormWithNothingChangedMovesNeitherTime() {
        var owner = viewerFor(newSub());
        var id = editing.add(owner, azbel("Azbel", "0", "2.16"));
        var first = facility(owner, id);

        editing.change(owner, id, azbel("Azbel", "0", "2.16"));
        // the same index written with more zeros is the same number
        editing.change(owner, id, azbel("Azbel", "0.0", "2.160000"));

        var again = facility(owner, id);
        assertThat(again.updatedAt()).isEqualTo(first.updatedAt());
        assertThat(again.indexEnteredAt()).isEqualTo(first.indexEnteredAt());
    }

    @Test
    void aChangedValueMovesUpdatedAtAndOnlyAChangedIndexMovesTheTimeItWasTyped() {
        var owner = viewerFor(newSub());
        var id = editing.add(owner, azbel("Azbel", "0", "2.16"));
        var first = facility(owner, id);

        editing.change(owner, id, azbel("Azbel", "1", "2.16"));
        var taxChanged = facility(owner, id);
        assertThat(taxChanged.updatedAt()).isAfter(first.updatedAt());
        assertThat(taxChanged.indexEnteredAt()).isEqualTo(first.indexEnteredAt());
        assertThat(taxChanged.terms().taxPercent()).isEqualByComparingTo("1");

        editing.change(owner, id, azbel("Azbel", "1", "2.2"));
        var indexChanged = facility(owner, id);
        assertThat(indexChanged.indexEnteredAt()).isAfter(first.indexEnteredAt());
        assertThat(indexChanged.updatedAt()).isAfter(taxChanged.updatedAt());

        editing.change(owner, id, new FacilityValues("Renamed", null, terms("1", "2.2")));
        var renamed = facility(owner, id);
        assertThat(renamed.name()).isEqualTo("Renamed");
        assertThat(renamed.structureType()).isNull();
        assertThat(renamed.indexEnteredAt()).isEqualTo(indexChanged.indexEnteredAt());
    }

    @Test
    void changingAFacilityMakesTheCostingOfADraftThatUsesItOutOfDate() {
        var owner = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"));
        var facility = queries.list(owner).getFirst().facility().id();
        entry.cost(owner, id);
        assertThat(projectQueries.details(owner, id).costing().outOfDate()).isEmpty();

        editing.change(owner, facility, azbel("Azbel", "1", "2"));

        assertThat(projectQueries.details(owner, id).costing().outOfDate()).contains(CostingStaleness.FACILITY_CHANGED);
    }

    @Test
    void deletingAFacilityClearsItFromTheDraftsThatUseItAndAddsOneToTheirVersion() {
        var owner = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"));
        var facility = queries.list(owner).getFirst().facility().id();
        entry.cost(owner, id);
        var version = versionOf(owner, id);
        assertThat(queries.list(owner).getFirst().draftProjects()).isEqualTo(1);

        var deleted = editing.delete(owner, facility);

        assertThat(deleted.draftProjectsChanged()).isEqualTo(1);
        assertThat(queries.list(owner)).isEmpty();
        assertThat(projects.find(owner, id).orElseThrow().config().defaultFacility()).isNull();
        // the version moved, so the costing made with the facility no longer looks current
        assertThat(versionOf(owner, id)).isEqualTo(version + 1);
        assertThat(projectQueries.details(owner, id).costing().outOfDate()).contains(CostingStaleness.PROJECT_EDITED);
    }

    @Test
    void deletingAFacilityLeavesAQuotedProjectAndItsQuoteAlone() {
        var owner = viewerFor(newSub());
        var id = haulers.project(owner, haulers.azbel(owner, "2"));
        var facility = queries.list(owner).getFirst().facility().id();
        entry.cost(owner, id);
        quoting.issue(owner, new IssueQuoteCommand(id, versionOf(owner, id), "Buyer", null, Instant.now().plus(Duration.ofDays(7))));
        var version = versionOf(owner, id);
        assertThat(queries.list(owner).getFirst().draftProjects()).isZero();

        var deleted = editing.delete(owner, facility);

        assertThat(deleted.draftProjectsChanged()).isZero();
        assertThat(versionOf(owner, id)).isEqualTo(version);
        // the quote holds the facility's values and not a reference to it
        assertThat(projectQueries.details(owner, id).costing().frozen()).isTrue();
        assertThat(projectQueries.details(owner, id).costing().costing().document().quote()).isNotNull();
    }

    @Test
    void anotherAccountsFacilityLooksMissing() {
        var owner = viewerFor(newSub());
        var stranger = viewerFor(newSub());
        var id = editing.add(owner, azbel("Mine", "0", "2"));

        assertThatThrownBy(() -> editing.change(stranger, id, azbel("Taken", "0", "2"))).isInstanceOf(FacilityNotFoundException.class);
        assertThatThrownBy(() -> editing.delete(stranger, id)).isInstanceOf(FacilityNotFoundException.class);
        assertThatThrownBy(() -> queries.find(stranger, id)).isInstanceOf(FacilityNotFoundException.class);
        assertThat(queries.list(stranger)).isEmpty();
        assertThat(facility(owner, id).name()).isEqualTo("Mine");
    }

    @Test
    void aBlankOrOverlongNameAndAPercentOutOfRangeAreRefusedWhateverThePageAllowed() {
        assertThatThrownBy(() -> new FacilityValues("   ", null, terms("0", "2"))).isInstanceOf(InvalidFacilityNameException.class);
        assertThatThrownBy(() -> new FacilityValues(null, null, terms("0", "2"))).isInstanceOf(InvalidFacilityNameException.class);
        assertThatThrownBy(() -> new FacilityValues("x".repeat(101), null, terms("0", "2"))).isInstanceOf(InvalidFacilityNameException.class);
        assertThat(new FacilityValues("x".repeat(100), null, terms("0", "2")).name()).hasSize(100);
        assertThatThrownBy(() -> terms("101", "2")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aStructureTypeProposesItsOwnBonusesAndIsFoundByItsKey() {
        assertThat(StructureType.AZBEL.timePercent()).isEqualByComparingTo("20");
        assertThat(StructureType.AZBEL.costPercent()).isEqualByComparingTo("4");
        assertThat(StructureType.AZBEL.materialPercent()).isEqualByComparingTo("1");
        assertThat(StructureType.ofKey("npc_station")).contains(StructureType.NPC_STATION);
        assertThat(StructureType.ofKey(" raitaru ")).contains(StructureType.RAITARU);
        assertThat(StructureType.ofKey("fortizar")).isEmpty();
        assertThat(StructureType.ofKey("")).isEmpty();
        assertThat(StructureType.ofKey(null)).isEmpty();
    }
}
