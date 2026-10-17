package kr.needteacher.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;

/** 일반 IT가 빈 정의를 바꿔 DatabaseIntegrityGuard를 제거하는 우회 경로를 막는다. */
@AnalyzeClasses(packages = "kr.needteacher", importOptions = ImportOption.OnlyIncludeTests.class)
class GuardBypassIntegrityTest {

    @ArchTest
    static final ArchRule testsOutsideArchitectureDoNotUseBeanFactoryPostProcessors = noClasses()
            .that()
            .resideOutsideOfPackage("kr.needteacher.architecture..")
            .and()
            .doNotHaveFullyQualifiedName("kr.needteacher.TestcontainersConfiguration")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(BeanFactoryPostProcessor.class);

    @ArchTest
    static final ArchRule testsOutsideArchitectureDoNotReferenceGuard = noClasses()
            .that()
            .resideOutsideOfPackage("kr.needteacher.architecture..")
            .and()
            .doNotHaveFullyQualifiedName("kr.needteacher.TestcontainersConfiguration")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName(DatabaseIntegrityGuard.class.getName());
}
