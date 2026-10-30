package com.eveassist.spike;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eveassist.spike.identity.Viewer;
import com.eveassist.spike.industry.IndustryService;
import com.eveassist.spike.industry.internal.application.PlanEditorView;
import com.eveassist.spike.industry.internal.application.PlanRepository;
import com.eveassist.spike.industry.internal.application.VisiblePlans;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.stereotype.Service;

class ArchitectureTest {
    private static final ApplicationModules MODULES = ApplicationModules.of(SpikeApplication.class);
    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("com.eveassist.spike");
    private static final JavaClasses FIXTURES = new ClassFileImporter()
            .importPackages("com.eveassist.fixture", "com.eveassist.spike.industry.internal.application");

    private static final ArchCondition<JavaMethod> TAKE_THE_VIEWER_FIRST =
            new ArchCondition<>("take the Viewer as first parameter") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    boolean satisfied = !method.getRawParameterTypes().isEmpty()
                            && method.getRawParameterTypes().getFirst().isEquivalentTo(Viewer.class);
                    events.add(new SimpleConditionEvent(method, satisfied, method.getFullName()));
                }
            };

    /** First half of C's safety: no use case can be called without saying who is asking. */
    private static final ArchRule USE_CASES_TAKE_THE_VIEWER = methods()
            .that().arePublic()
            .and().areDeclaredInClassesThat().resideInAPackage("..industry.internal.application..")
            .and().areDeclaredInClassesThat().areAnnotatedWith(Service.class)
            .should(TAKE_THE_VIEWER_FIRST);

    /** Second half: having the viewer is not enough, so plans can only be loaded through VisiblePlans. */
    private static final ArchRule ONLY_VISIBLE_PLANS_LOADS_PLANS = noClasses()
            .that().resideInAnyPackage("..industry.internal.application..", "..industry.internal.web..")
            .and(DescribedPredicate.not(JavaClass.Predicates.equivalentTo(VisiblePlans.class)))
            .and(DescribedPredicate.not(JavaClass.Predicates.equivalentTo(PlanRepository.class)))
            .should().dependOnClassesThat().areAssignableTo(PlanRepository.class);

    /** The guardrail from code-structure.md: nothing inside the module calls its own facade. */
    private static final ArchRule NOTHING_INSIDE_CALLS_THE_FACADE = noClasses()
            .that().resideInAPackage("..industry.internal..")
            .should().dependOnClassesThat().areAssignableTo(IndustryService.class);

    @Test
    void moduleBoundariesHold() {
        MODULES.verify();
    }

    @Test
    void useCasesTakeTheViewer() {
        USE_CASES_TAKE_THE_VIEWER.check(PRODUCTION);
    }

    @Test
    void onlyVisiblePlansLoadsPlans() {
        ONLY_VISIBLE_PLANS_LOADS_PLANS.check(PRODUCTION);
    }

    @Test
    void nothingInsideCallsTheFacade() {
        NOTHING_INSIDE_CALLS_THE_FACADE.check(PRODUCTION);
    }

    @Test
    void ruleCatchesAUseCaseWithoutTheViewer() {
        assertThatThrownBy(() -> USE_CASES_TAKE_THE_VIEWER.check(FIXTURES))
                .hasMessageContaining("ViewerlessUseCase");
    }

    @Test
    void ruleCatchesAUseCaseThatIgnoresTheViewer() {
        assertThatThrownBy(() -> ONLY_VISIBLE_PLANS_LOADS_PLANS.check(FIXTURES))
                .hasMessageContaining("ViewerIgnoringUseCase");
    }

    @Test
    void screenTypesAreHiddenFromOtherModules() {
        var industry = MODULES.getModuleByName("industry").orElseThrow();
        assertThat(industry.getNamedInterfaces().getUnnamedInterface().contains(PlanEditorView.class)).isFalse();
    }
}
