package com.eveassist.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.stereotype.Component;

/**
 * EveAssist's own rules from documentation/architecture/code-structure.md. The base package is a parameter so that each
 * rule can also be run against a fixture that breaks it on purpose.
 */
final class ArchitectureRules {

    private final String base;

    ArchitectureRules(String base) {
        this.base = base;
    }

    ArchRule domainStaysPlain() {
        return noClasses().that().resideInAPackage(base + "..internal.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..",
                        "com.fasterxml.jackson..", "tools.jackson..")
                .allowEmptyShould(true);
    }

    ArchRule onlyEsiAndCharactersUseAnHttpClient() {
        return noClasses().that().resideInAPackage(base + "..")
                .and().resideOutsideOfPackages(base + ".esi..", base + ".characters..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web.client..", "org.springframework.web.reactive.function.client..",
                        "java.net.http..", "org.apache.hc..", "okhttp3..")
                .allowEmptyShould(true);
    }

    /** Beans such as the facade are the module's other public classes; everything else there is data. */
    ArchRule moduleDataTypesAreRecords() {
        return classes().that().resideInAPackage(base + ".*")
                .and().areTopLevelClasses()
                .and().areNotInterfaces()
                .and().areNotEnums()
                .and().areNotMetaAnnotatedWith(Component.class)
                .and().doNotHaveSimpleName("package-info")
                .should().beAssignableTo(Record.class)
                .allowEmptyShould(true);
    }

    ArchRule namesCarryMeaningNotMechanism() {
        return noClasses().that().resideInAPackage(base + "..")
                .should().haveSimpleNameEndingWith("Dto")
                .orShould().haveSimpleNameEndingWith("Impl")
                .orShould(beAnInterfaceNamedLikeIFoo())
                .allowEmptyShould(true);
    }

    /** Catches a facade called from inside its own module, which Modulith does not notice. */
    ArchRule moduleInternalsUseOnlyDataFromTheirBasePackage() {
        return classes().that().resideInAPackage(base + ".*.internal..")
                .should(dependOnOnlyDataInOwnBasePackage())
                .allowEmptyShould(true);
    }

    /** Applies to every class that holds use cases, not only those annotated with a particular stereotype. */
    ArchRule everyUseCaseMethodTakesTheViewerFirst() {
        return classes().that(areUseCaseClasses())
                .should(havePublicMethodsTakingTheViewerFirst())
                .allowEmptyShould(true);
    }

    /** Applies to every class in the application, not only to internal.application and internal.web. */
    ArchRule plansLoadOnlyThroughAVisibleClass() {
        return noClasses().that().resideInAPackage(base + "..")
                .and().resideOutsideOfPackage("..internal.persistence..")
                .and(not(areVisibleClassesInApplication()))
                .should().dependOnClassesThat(areRepositoriesIn(base + ".industry.internal.."))
                .allowEmptyShould(true);
    }

    ArchRule queriesAndWritesDoNotStartFetches() {
        return noClasses().that().resideInAPackage(base + "..internal.application..")
                .and().haveNameMatching(".*(Queries|Editing)")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("FetchQueue")
                .allowEmptyShould(true);
    }

    ArchRule facadesDoNotReachPageEntryUseCases() {
        return noClasses().that().resideInAPackage(base + ".*")
                .should().dependOnClassesThat(arePageEntryUseCases())
                .allowEmptyShould(true);
    }

    private DescribedPredicate<JavaClass> areUseCaseClasses() {
        return describe("are use case classes", c -> c.getPackageName().contains(".internal.application")
                && c.getPackageName().startsWith(base + ".")
                && c.isTopLevelClass()
                && !c.isInterface()
                && !c.isEnum()
                && !c.isRecord()
                && !c.isAssignableTo(Throwable.class)
                && !c.getSimpleName().equals("package-info"));
    }

    private ArchCondition<JavaClass> havePublicMethodsTakingTheViewerFirst() {
        var viewer = base + ".identity.Viewer";
        return new ArchCondition<>("have public methods taking the Viewer as first parameter") {
            @Override
            public void check(JavaClass useCases, ConditionEvents events) {
                for (JavaMethod method : useCases.getMethods()) {
                    if (method.getModifiers().contains(JavaModifier.PUBLIC)
                            && !method.getModifiers().contains(JavaModifier.STATIC)
                            && !method.getModifiers().contains(JavaModifier.SYNTHETIC)
                            && !takesViewerFirst(method, viewer)) {
                        events.add(violated(useCases, method.getFullName() + " does not take the Viewer first"));
                    }
                }
            }
        };
    }

    private static boolean takesViewerFirst(JavaMethod method, String viewer) {
        var parameters = method.getRawParameterTypes();
        return !parameters.isEmpty() && parameters.getFirst().getName().equals(viewer);
    }

    private ArchCondition<JavaClass> dependOnOnlyDataInOwnBasePackage() {
        return new ArchCondition<>("depend only on records, enums and interfaces in their module's base package") {
            @Override
            public void check(JavaClass internal, ConditionEvents events) {
                var moduleBase = moduleBaseOf(internal);
                internal.getDirectDependenciesFromSelf().stream()
                        .filter(d -> d.getTargetClass().getPackageName().equals(moduleBase))
                        .filter(d -> !isData(d.getTargetClass()))
                        .forEach(d -> events.add(violated(internal, d.getDescription())));
            }
        };
    }

    private String moduleBaseOf(JavaClass internal) {
        var name = internal.getPackageName();
        return name.substring(0, name.indexOf(".internal"));
    }

    private static boolean isData(JavaClass target) {
        return target.isRecord() || target.isEnum();
    }

    private static ArchCondition<JavaClass> beAnInterfaceNamedLikeIFoo() {
        return new ArchCondition<>("be an interface named I followed by a capital letter") {
            @Override
            public void check(JavaClass c, ConditionEvents events) {
                var named = c.isInterface() && c.getSimpleName().matches("I[A-Z].*");
                events.add(new SimpleConditionEvent(c, named, c.getName() + " is an interface named like IFoo"));
            }
        };
    }

    private static DescribedPredicate<JavaClass> areVisibleClassesInApplication() {
        return describe("are Visible classes in internal.application",
                c -> c.getSimpleName().startsWith("Visible") && c.getPackageName().endsWith(".internal.application"));
    }

    private static DescribedPredicate<JavaClass> areRepositoriesIn(String packagePattern) {
        var prefix = packagePattern.substring(0, packagePattern.length() - 2);
        return describe("are repositories in " + packagePattern,
                c -> c.getSimpleName().endsWith("Repository") && c.getPackageName().startsWith(prefix));
    }

    private static DescribedPredicate<JavaClass> arePageEntryUseCases() {
        return describe("are page-entry use cases",
                c -> c.getSimpleName().endsWith("Entry") && c.getPackageName().endsWith(".internal.application"));
    }
}
