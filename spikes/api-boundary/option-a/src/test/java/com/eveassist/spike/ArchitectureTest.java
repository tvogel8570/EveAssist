package com.eveassist.spike;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.spike.industry.PlanEditorView;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ArchitectureTest {
    private static final ApplicationModules MODULES = ApplicationModules.of(SpikeApplication.class);
    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("com.eveassist.spike");
    private static final JavaClasses FIXTURES = new ClassFileImporter()
            .importPackages("com.eveassist.fixture", "com.eveassist.spike.industry.internal.application");

    /** With visibility checked only in the facade, a page that skips the facade skips the check. */
    private static final ArchRule PAGES_GO_THROUGH_THE_FACADE = noClasses()
            .that().resideInAPackage("..industry.internal.web..")
            .should().dependOnClassesThat().resideInAPackage("..industry.internal.application..");

    @Test
    void moduleBoundariesHold() {
        MODULES.verify();
    }

    @Test
    void pagesGoThroughTheFacade() {
        PAGES_GO_THROUGH_THE_FACADE.check(PRODUCTION);
    }

    @Test
    void ruleCatchesAPageThatSkipsTheFacade() {
        assertThatThrownBy(() -> PAGES_GO_THROUGH_THE_FACADE.check(FIXTURES))
                .hasMessageContaining("BypassingController");
    }

    @Test
    void screenTypesAreExposedToOtherModules() {
        var industry = MODULES.getModuleByName("industry").orElseThrow();
        assertThat(industry.getNamedInterfaces().getUnnamedInterface().contains(PlanEditorView.class)).isTrue();
    }
}
