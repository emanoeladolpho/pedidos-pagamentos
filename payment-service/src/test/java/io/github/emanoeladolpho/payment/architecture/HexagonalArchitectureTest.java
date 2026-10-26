package io.github.emanoeladolpho.payment.architecture;

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
@AnalyzeClasses(packages = "io.github.emanoeladolpho.payment", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    private static final String[] FRAMEWORKS = {
            "org.springframework..", "jakarta..", "javax.persistence..", "org.hibernate..",
            "com.fasterxml..", "org.apache.kafka..", "org.mapstruct..", "lombok.."
    };

    @ArchTest
    static final ArchRule onion = onionArchitecture()
            .domainModels("..payment.domain..")
            .applicationServices("..payment.application..")
            .adapter("web", "..payment.infrastructure.adapter.in.web..")
            .adapter("messaging-in", "..payment.infrastructure.adapter.in.messaging..")
            .adapter("persistence", "..payment.infrastructure.adapter.out.persistence..")
            .adapter("messaging-out", "..payment.infrastructure.adapter.out.messaging..")
            .adapter("gateway", "..payment.infrastructure.adapter.out.gateway..")
            .withOptionalLayers(true)
            // infrastructure.config é o composition root: instancia os casos de uso e liga as portas aos adapters
            .ignoreDependency(resideInAPackage("..payment.infrastructure.config.."), alwaysTrue());

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage("..payment.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
            .because("o domínio deve ser Java puro");

    @ArchTest
    static final ArchRule applicationIsFrameworkFree = noClasses()
            .that().resideInAPackage("..payment.application..")
            .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS)
            .because("casos de uso dependem apenas de portas, nunca de tecnologia");

    @ArchTest
    static final ArchRule nothingDependsOnInfrastructure = noClasses()
            .that().resideInAnyPackage("..payment.domain..", "..payment.application..")
            .should().dependOnClassesThat().resideInAPackage("..payment.infrastructure..");

    @ArchTest
    static final ArchRule useCasesImplementInputPorts = classes()
            .that().resideInAPackage("..payment.application.usecase..")
            .and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("Service")
            .andShould().implement(describe("uma porta de entrada (..application.port.in..)",
                    (JavaClass port) -> port.getPackageName().contains(".application.port.in")));

    @ArchTest
    static final ArchRule portsAreInterfaces = classes()
            .that().resideInAPackage("..payment.application.port.out..")
            .and().areTopLevelClasses()
            .should().beInterfaces();
}
