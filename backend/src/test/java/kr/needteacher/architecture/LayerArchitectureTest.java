package kr.needteacher.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;

/** 보호 파일. 규칙을 완화하려면 사용자 리뷰가 필요하다 (docs/proposals/test-harness.md 3절). */
@AnalyzeClasses(packages = "kr.needteacher", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerArchitectureTest {

    @ArchTest
    static final ArchRule controllersDoNotTouchRepositories = noClasses()
            .that()
            .areMetaAnnotatedWith(Controller.class)
            .should()
            .dependOnClassesThat()
            .areAssignableTo(Repository.class)
            .orShould()
            .dependOnClassesThat()
            .areMetaAnnotatedWith(org.springframework.stereotype.Repository.class);

    @ArchTest
    static final ArchRule servicesDoNotDependOnControllers = noClasses()
            .that()
            .areMetaAnnotatedWith(Service.class)
            .should()
            .dependOnClassesThat()
            .areMetaAnnotatedWith(Controller.class);

    // 아직 리포지토리가 없으므로 빈 대상 허용
    @ArchTest
    static final ArchRule repositoriesDependOnNothingAbove = noClasses()
            .that()
            .areAssignableTo(Repository.class)
            .should()
            .dependOnClassesThat()
            .areMetaAnnotatedWith(Service.class)
            .orShould()
            .dependOnClassesThat()
            .areMetaAnnotatedWith(Controller.class)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule featurePackagesAreFreeOfCycles =
            slices().matching("kr.needteacher.(*)..").should().beFreeOfCycles();
}
