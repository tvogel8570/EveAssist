package com.eveassist.architecture;

import com.eveassist.EveAssistApplication;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.jmolecules.archunit.JMoleculesArchitectureRules;
import org.jmolecules.archunit.JMoleculesArchitectureRules.VerificationDepth;
import org.jmolecules.archunit.JMoleculesDddRules;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.VerificationOptions;
import org.springframework.modulith.docs.Documenter;

class ArchitectureTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(EveAssistApplication.class);
    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.eveassist");
    private static final ArchitectureRules RULES = new ArchitectureRules("com.eveassist");

    /**
     * By default verify() already runs the jMolecules DDD rules and the hexagonal rule at LENIENT depth. The options
     * replace those defaults, so each rule runs once, and the hexagonal rule at STRICT as code-structure.md asks.
     */
    @Test
    void modulesAndJMoleculesRulesHold() {
        MODULES.verify(VerificationOptions.defaults().withAdditionalVerifications(
                JMoleculesDddRules.all(),
                JMoleculesArchitectureRules.ensureHexagonal(VerificationDepth.STRICT)));
    }

    @Test
    void domainStaysPlain() {
        RULES.domainStaysPlain().check(PRODUCTION);
    }

    @Test
    void onlyEsiAndCharactersUseAnHttpClient() {
        RULES.onlyEsiAndCharactersUseAnHttpClient().check(PRODUCTION);
    }

    @Test
    void moduleDataTypesAreRecords() {
        RULES.moduleDataTypesAreRecords().check(PRODUCTION);
    }

    @Test
    void namesCarryMeaningNotMechanism() {
        RULES.namesCarryMeaningNotMechanism().check(PRODUCTION);
    }

    @Test
    void moduleInternalsUseOnlyDataFromTheirBasePackage() {
        RULES.moduleInternalsUseOnlyDataFromTheirBasePackage().check(PRODUCTION);
    }

    @Test
    void everyUseCaseMethodTakesTheViewerFirst() {
        RULES.everyUseCaseMethodTakesTheViewerFirst().check(PRODUCTION);
    }

    @Test
    void plansLoadOnlyThroughAVisibleClass() {
        RULES.plansLoadOnlyThroughAVisibleClass().check(PRODUCTION);
    }

    @Test
    void queriesAndWritesDoNotStartFetches() {
        RULES.queriesAndWritesDoNotStartFetches().check(PRODUCTION);
    }

    @Test
    void facadesDoNotReachPageEntryUseCases() {
        RULES.facadesDoNotReachPageEntryUseCases().check(PRODUCTION);
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}
