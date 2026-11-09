package com.eveassist.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jmolecules.archunit.JMoleculesArchitectureRules;
import org.jmolecules.archunit.JMoleculesArchitectureRules.VerificationDepth;
import org.jmolecules.archunit.JMoleculesDddRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** A rule that cannot fail proves nothing, so each one is run against code that breaks it and code that does not. */
class ArchitectureRulesFixtureTest {

    private static final JavaClasses BROKEN = new ClassFileImporter().importPackages("archfixture.broken");
    private static final JavaClasses CLEAN = new ClassFileImporter().importPackages("archfixture.clean");
    private static final ArchitectureRules ON_BROKEN = new ArchitectureRules("archfixture.broken");
    private static final ArchitectureRules ON_CLEAN = new ArchitectureRules("archfixture.clean");

    static Stream<Arguments> rules() {
        return Stream.of(
                rule(ArchitectureRules::domainStaysPlain, "SpringAwarePlan"),
                rule(ArchitectureRules::domainStaysPlain, "Jackson3AwarePlan"),
                rule(ArchitectureRules::onlyEsiAndCharactersUseAnHttpClient, "MarketHttpCaller"),
                rule(ArchitectureRules::moduleDataTypesAreRecords, "PlanSummaryClass"),
                rule(ArchitectureRules::namesCarryMeaningNotMechanism, "PlanDto"),
                rule(ArchitectureRules::namesCarryMeaningNotMechanism, "PlanLookupImpl"),
                rule(ArchitectureRules::namesCarryMeaningNotMechanism, "IPlanLookup"),
                rule(ArchitectureRules::moduleInternalsUseOnlyDataFromTheirBasePackage, "FacadeCallingEditing"),
                rule(ArchitectureRules::moduleInternalsUseOnlyDataFromTheirBasePackage, "UnmarkedPortUser"),
                rule(ArchitectureRules::everyUseCaseMethodTakesTheViewerFirst, "UnannotatedUseCases"),
                rule(ArchitectureRules::everyUseCaseMethodTakesTheViewerFirst, "ViewerLastUseCases"),
                rule(ArchitectureRules::everyRepositoryMethodTakesTheViewerFirst, "PlanRepository"),
                rule(ArchitectureRules::everyRepositoryMethodTakesTheViewerFirst, "JdbcPlanRepository"),
                rule(ArchitectureRules::queriesAndWritesDoNotStartFetches, "FetchingQueries"),
                rule(ArchitectureRules::facadesDoNotReachPageEntryUseCases, "BrokenFacade"));
    }

    private static Arguments rule(Function<ArchitectureRules, ArchRule> rule, String offender) {
        return Arguments.of(rule, offender);
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("rules")
    void ruleCatchesItsFixture(Function<ArchitectureRules, ArchRule> rule, String offender) {
        assertThatThrownBy(() -> rule.apply(ON_BROKEN).check(BROKEN))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(offender);
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("rules")
    void ruleAcceptsCleanCode(Function<ArchitectureRules, ArchRule> rule, String offender) {
        assertThatCode(() -> rule.apply(ON_CLEAN).check(CLEAN)).doesNotThrowAnyException();
    }

    @Test
    void hexagonalRuleCatchesAnAdapterReachedFromTheApplicationLayer() {
        assertThatThrownBy(() -> JMoleculesArchitectureRules.ensureHexagonal(VerificationDepth.STRICT)
                .check(BROKEN))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UseCasesCallingAdapter");
    }

    @Test
    void dddRulesCatchAnAggregateHoldingAnotherAggregate() {
        assertThatThrownBy(() -> JMoleculesDddRules.all().check(BROKEN))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("OwningAggregate");
    }

    @Test
    void jmoleculesRulesAcceptCleanCode() {
        assertThatCode(() -> {
            JMoleculesArchitectureRules.ensureHexagonal(VerificationDepth.STRICT).check(CLEAN);
            JMoleculesDddRules.all().check(CLEAN);
        }).doesNotThrowAnyException();
    }
}
