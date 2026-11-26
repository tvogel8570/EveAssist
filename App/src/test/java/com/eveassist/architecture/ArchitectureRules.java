package com.eveassist.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.PackageMatcher;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * EveAssist's own rules from documentation/architecture/code-structure.md. The base package is a parameter so that each
 * rule can also be run against a fixture that breaks it on purpose.
 *
 * <p>A module's layers are directly under {@code internal}, or under a feature's package there ({@code internal.<feature>.<layer>}), and
 * the rules find a layer in both places.
 */
final class ArchitectureRules {

    /** The packages of a layer, with whatever is between {@code internal} and it, and everything under it. */
    private static final String DOMAIN = "..internal..domain..";
    private static final String APPLICATION = "..internal..application..";
    /** The mark of a helper that is not a use case, under the base package. */
    private static final String NO_ACCOUNT_DATA = ".identity.ReadsNoAccountData";

    private final String base;

    ArchitectureRules(String base) {
        this.base = base;
    }

    ArchRule domainStaysPlain() {
        return noClasses().that().resideInAPackage(base + DOMAIN)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..",
                        "com.fasterxml.jackson..", "tools.jackson..")
                .allowEmptyShould(true);
    }

    /**
     * Where outbound HTTP calls may start stays a list that can be read: ESI and EVE SSO (the esi and characters modules),
     * and Keycloak's admin API, which identity calls to delete a user. The exemption is the one class, not the module, so
     * nothing else in identity can start a call unnoticed.
     */
    ArchRule onlyEsiCharactersAndKeycloakAdminUseAnHttpClient() {
        return noClasses().that().resideInAPackage(base + "..")
                .and().resideOutsideOfPackages(base + ".esi..", base + ".characters..")
                .and().haveNameNotMatching(base.replace(".", "\\.") + "\\.identity\\.internal\\.KeycloakAdmin(\\$.*)?")
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
                .should(havePublicMethodsTakingWhoActsFirst(base + ".identity.Viewer", base + ".identity.Visitor"))
                .allowEmptyShould(true);
    }

    /**
     * Covers ports and implementations alike, so that a repository method cannot reach a plan without a viewer. It only
     * proves the viewer is passed: each repository has a test that another account gets nothing back.
     */
    ArchRule everyRepositoryMethodTakesTheViewerFirst() {
        return classes().that(areRepositoriesIn(base + ".industry.internal"))
                .should(havePublicMethodsTakingWhoActsFirst(base + ".identity.Viewer"))
                .allowEmptyShould(true);
    }

    /**
     * The mark takes a class out of the viewer-first rule, so its claim is checked: such a class depends on no repository and on no database access.
     * Only what the class itself depends on is checked, not what its collaborators do.
     */
    ArchRule helpersMarkedAsReadingNoAccountDataReachNone() {
        return noClasses().that().areAnnotatedWith(base + NO_ACCOUNT_DATA)
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .orShould().dependOnClassesThat().resideInAnyPackage("java.sql..", "javax.sql..", "org.springframework.jdbc..")
                .allowEmptyShould(true);
    }

    /**
     * Which feature folders of {@code industry.internal} may use which, besides their own. Sub-folders, such as {@code project.quote}, belong to their
     * feature and are not limited among themselves. Nothing lists {@code tryplan}, so nothing may use it. A class outside these folders is refused, so
     * that a new feature is added here on purpose.
     */
    private static final Map<String, Set<String>> INDUSTRY_FEATURE_MAY_USE = Map.of(
            "shared", Set.of(),
            "costing", Set.of("shared"),
            "facility", Set.of("costing", "shared"),
            "plan", Set.of("costing", "shared"),
            "tryplan", Set.of("costing", "plan", "shared"),
            "project", Set.of("costing", "facility", "plan", "shared"));

    ArchRule industryFeaturesUseOnlyWhatTheyMay() {
        return featuresUseOnlyWhatTheyMay(base + ".industry.internal.", INDUSTRY_FEATURE_MAY_USE);
    }

    /**
     * Which feature folders of {@code characters.internal} may use which, besides their own. Only {@code linking} may use {@code tokens}, which holds
     * the stored tokens and their encryption. An entry such as {@code linking.CharacterRepository} allows that one class, with its nested types, and
     * nothing else of the folder: it is how {@code tokens} and {@code membership} reach back into {@code linking}.
     */
    private static final Map<String, Set<String>> CHARACTERS_FEATURE_MAY_USE = Map.of(
            "sso", Set.of(),
            "tokens", Set.of("sso", "linking.CharacterRepository"),
            "membership", Set.of("linking.CharacterDetails"),
            "linking", Set.of("membership", "sso", "tokens"));

    ArchRule charactersFeaturesUseOnlyWhatTheyMay() {
        return featuresUseOnlyWhatTheyMay(base + ".characters.internal.", CHARACTERS_FEATURE_MAY_USE);
    }

    private static ArchRule featuresUseOnlyWhatTheyMay(String internal, Map<String, Set<String>> mayUse) {
        return classes().that().resideInAPackage(internal.substring(0, internal.length() - 1) + "..")
                .should(useOnlyTheFeaturesTheirsMay(internal, mayUse))
                .allowEmptyShould(true);
    }

    private static ArchCondition<JavaClass> useOnlyTheFeaturesTheirsMay(String internal, Map<String, Set<String>> mayUse) {
        return new ArchCondition<>("use only what their own feature may use") {
            @Override
            public void check(JavaClass from, ConditionEvents events) {
                var feature = featureOf(from, internal);
                if (!mayUse.containsKey(feature)) {
                    events.add(violated(from, from.getName() + " is in no feature folder that the table of ArchitectureRules lists"));
                    return;
                }
                from.getDirectDependenciesFromSelf().forEach(dependency -> {
                    var target = dependency.getTargetClass().getBaseComponentType();
                    var used = featureOf(target, internal);
                    if (used != null && !used.equals(feature) && !mayUse.get(feature).contains(used)
                            && !mayUse.get(feature).contains(used + "." + topLevelNameOf(target))) {
                        events.add(violated(from, "feature " + feature + " may not use feature " + used + ": " + dependency.getDescription()));
                    }
                });
            }
        };
    }

    /** The simple name of the class, or of the class it is nested in. */
    private static String topLevelNameOf(JavaClass c) {
        var name = c.getName().substring(c.getPackageName().length() + 1);
        return name.contains("$") ? name.substring(0, name.indexOf('$')) : name;
    }

    /** The first package under {@code internal}, an empty string for a class directly in it, or null for a class that is not under it. */
    private static String featureOf(JavaClass c, String internal) {
        var name = c.getPackageName() + ".";
        if (!name.startsWith(internal)) {
            return null;
        }
        var end = name.indexOf('.', internal.length());
        return end < 0 ? "" : name.substring(internal.length(), end);
    }

    ArchRule queriesAndWritesDoNotStartFetches() {
        return noClasses().that().resideInAPackage(base + APPLICATION)
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
        return describe("are use case classes", c -> PackageMatcher.of(APPLICATION).matches(c.getPackageName())
                && c.getPackageName().startsWith(base + ".")
                && c.isTopLevelClass()
                && !c.isInterface()
                && !c.isEnum()
                && !c.isRecord()
                && !c.isAssignableTo(Throwable.class)
                && !c.isAnnotatedWith(base + NO_ACCOUNT_DATA)
                && !c.getSimpleName().equals("package-info"));
    }

    /**
     * A use case acts for a signed-in {@code Viewer}, or for a {@code Visitor} when nobody is (a plan tried without an
     * account). A repository takes only a {@code Viewer}, so a visitor can never reach an account's records.
     */
    private ArchCondition<JavaClass> havePublicMethodsTakingWhoActsFirst(String... whoActs) {
        return new ArchCondition<>("have public methods taking who acts as first parameter") {
            @Override
            public void check(JavaClass useCases, ConditionEvents events) {
                for (JavaMethod method : useCases.getMethods()) {
                    if (method.getModifiers().contains(JavaModifier.PUBLIC)
                            && !method.getModifiers().contains(JavaModifier.STATIC)
                            && !method.getModifiers().contains(JavaModifier.SYNTHETIC)
                            && !takesWhoActsFirst(method, whoActs)) {
                        events.add(violated(useCases, method.getFullName() + " does not take the "
                                + String.join(" or ", java.util.Arrays.stream(whoActs)
                                        .map(name -> name.substring(name.lastIndexOf('.') + 1)).toList()) + " first"));
                    }
                }
            }
        };
    }

    private static boolean takesWhoActsFirst(JavaMethod method, String... whoActs) {
        var parameters = method.getRawParameterTypes();
        return !parameters.isEmpty() && java.util.Arrays.asList(whoActs).contains(parameters.getFirst().getName());
    }

    private ArchCondition<JavaClass> dependOnOnlyDataInOwnBasePackage() {
        return new ArchCondition<>("depend only on records, enums and interfaces in their module's base package") {
            @Override
            public void check(JavaClass internal, ConditionEvents events) {
                var moduleBase = moduleBaseOf(internal);
                internal.getDirectDependenciesFromSelf().stream()
                        .filter(d -> d.getTargetClass().getPackageName().equals(moduleBase))
                        .filter(d -> !isData(d.getTargetClass()))
                        .filter(d -> !isImplementingItsOwnInterface(internal, d.getTargetClass()))
                        .filter(d -> !isOutboundPort(d.getTargetClass()))
                        .forEach(d -> events.add(violated(internal, d.getDescription())));
            }
        };
    }

    private String moduleBaseOf(JavaClass internal) {
        var name = internal.getPackageName();
        return name.substring(0, name.indexOf(".internal"));
    }

    /** An interface in the base package is the module's API, and its production implementation lives in internal. */
    private static boolean isImplementingItsOwnInterface(JavaClass internal, JavaClass target) {
        return target.isInterface() && internal.isAssignableTo(target.getName());
    }

    /**
     * A port that the module calls and another module implements, such as the access tokens the {@code esi} module asks
     * the {@code characters} module for. It has to be in the base package for the other module to see it, and calling it
     * is the point, so it is not the facade-called-from-inside case. It must be marked, so that a facade cannot pass as one.
     */
    private static boolean isOutboundPort(JavaClass target) {
        return target.isInterface()
                && target.isAnnotatedWith(org.jmolecules.architecture.hexagonal.SecondaryPort.class);
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

    private static DescribedPredicate<JavaClass> areRepositoriesIn(String prefix) {
        return describe("are repositories in " + prefix,
                c -> c.getSimpleName().endsWith("Repository") && c.getPackageName().startsWith(prefix));
    }

    private static DescribedPredicate<JavaClass> arePageEntryUseCases() {
        return describe("are page-entry use cases",
                // the layer's own package and not one under it
                c -> c.getSimpleName().endsWith("Entry") && PackageMatcher.of("..internal..application").matches(c.getPackageName()));
    }
}
