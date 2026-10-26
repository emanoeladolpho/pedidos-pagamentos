package io.github.emanoeladolpho.order.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

/**
 * Garante que as dependências apontam sempre para dentro:
 * infrastructure → application → domain. Qualquer violação quebra o build.
 */
@AnalyzeClasses(packages = "io.github.emanoeladolpho.order", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    private static final String[] FRAMEWORKS = {
            "org.springframework..", "jakarta..", "javax.persistence..", "org.hibernate..",
            "com.fasterxml..", "org.apache.kafka..", "org.mapstruct..", "lombok.."
    };

    @ArchTest
    static final ArchRule onion = onionArchitecture()
            .domainModels("..order.domain..")
            .applicationServices("..order.application..")
            .adapter("web", "..order.infrastructure.adapter.in.web..")
            .adapter("messaging-in", "..order.infrastructure.adapter.in.messaging..")
            .adapter("persistence", "..order.infrastructure.adapter.out.persistence..")
            .adapter("messaging-out", "..order.infrastructure.adapter.out.messaging..")
            .withOptionalLayers(true)
            // infrastructure.config é o composition root: instancia os casos de uso e liga as portas aos adapters
            .ignoreDependency(resideInAPackage("..order.infrastructure.config.."), alwaysTrue());

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage("..order.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
            .because("o domínio deve ser Java puro");

    @ArchTest
    static final ArchRule applicationIsFrameworkFree = noClasses()
            .that().resideInAPackage("..order.application..")
            .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
            .because("casos de uso dependem apenas de portas, nunca de tecnologia");

    @ArchTest
    static final ArchRule nothingDependsOnInfrastructure = noClasses()
            .that().resideInAnyPackage("..order.domain..", "..order.application..")
            .should().dependOnClassesThat().resideInAPackage("..order.infrastructure..");

    @ArchTest
    static final ArchRule useCasesImplementInputPorts = classes()
            .that().resideInAPackage("..order.application.usecase..")
            .and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("Service")
            .andShould().implement(describe("uma porta de entrada (..application.port.in..)",
                    (JavaClass port) -> port.getPackageName().contains(".application.port.in")));

    @ArchTest
    static final ArchRule portsAreInterfaces = classes()
            .that().resideInAPackage("..order.application.port.out..")
            .and().areTopLevelClasses()
            .should().beInterfaces();
}
