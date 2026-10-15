package kr.needteacher.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.EntityManagerFactory;
import java.util.Arrays;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

/**
 * 보호 파일. 통합 테스트(*IT)가 실제 PostgreSQL 대신 가짜로 바꿔치기해 수치 게이트만 통과하는 것을 막는다 (T-013 레드팀).
 * 외부 연동은 목 대신 WireMock·Testcontainers로 대체한다.
 */
@AnalyzeClasses(packages = "kr.needteacher", importOptions = ImportOption.OnlyIncludeTests.class)
class TestIntegrityTest {

    private static final String MOCKITO_BEAN = "org.springframework.test.context.bean.override.mockito.MockitoBean";
    private static final String MOCKITO_SPY_BEAN =
            "org.springframework.test.context.bean.override.mockito.MockitoSpyBean";
    private static final String TEST_BEAN = "org.springframework.test.context.bean.override.convention.TestBean";
    private static final String TESTCONTAINERS_CONFIGURATION = "kr.needteacher.TestcontainersConfiguration";

    @ArchTest
    static final ArchRule integrationTestFieldsDoNotOverrideBeans = noFields()
            .that()
            .areDeclaredInClassesThat()
            .haveSimpleNameEndingWith("IT")
            .should()
            .beAnnotatedWith(MOCKITO_BEAN)
            .orShould()
            .beAnnotatedWith(MOCKITO_SPY_BEAN)
            .orShould()
            .beAnnotatedWith(TEST_BEAN)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule integrationTestClassesDoNotOverrideBeans = noClasses()
            .that()
            .haveSimpleNameEndingWith("IT")
            .should()
            .beAnnotatedWith(MOCKITO_BEAN)
            .orShould()
            .beAnnotatedWith(MOCKITO_SPY_BEAN);

    // 주석·미사용 import로 흉내 내지 못하게 바이트코드의 실제 애노테이션 값을 본다 (T-014)
    @ArchTest
    static final ArchRule integrationTestsUseRealPostgres = classes()
            .that()
            .haveSimpleNameEndingWith("IT")
            .should()
            .beAnnotatedWith(new DescribedPredicate<JavaAnnotation<?>>("@Import(TestcontainersConfiguration.class)") {
                @Override
                public boolean test(JavaAnnotation<?> annotation) {
                    return annotation.getRawType().isEquivalentTo(Import.class)
                            && annotation
                                    .get("value")
                                    .filter(JavaClass[].class::isInstance)
                                    // 다른 패키지에 만든 같은 이름의 가짜를 막으려고 전체 이름으로 비교한다 (T-016)
                                    .map(value -> Arrays.stream((JavaClass[]) value)
                                            .anyMatch(c -> c.getName().equals(TESTCONTAINERS_CONFIGURATION)))
                                    .orElse(false);
                }
            });

    // HikariDataSource 같은 하위 타입 반환도 포함 (T-016)
    @ArchTest
    static final ArchRule testCodeDoesNotReplaceDataSource = noMethods()
            .that()
            .haveRawReturnType(JavaClass.Predicates.assignableTo(DataSource.class))
            .should()
            .beAnnotatedWith(Bean.class)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule testCodeDoesNotReplacePersistenceInfrastructure = noMethods()
            .that()
            .haveRawReturnType(JavaClass.Predicates.assignableTo(EntityManagerFactory.class)
                    .or(JavaClass.Predicates.assignableTo(LocalContainerEntityManagerFactoryBean.class))
                    .or(JavaClass.Predicates.assignableTo(Flyway.class)))
            .should()
            .beAnnotatedWith(Bean.class)
            .allowEmptyShould(true);

    // 내장 DB로 바꿔치기. 패키지 경로를 붙여 써도 바이트코드에서는 같은 애노테이션이다 (T-016)
    @ArchTest
    static final ArchRule testCodeDoesNotReplaceDatabase = noClasses()
            .should()
            .beAnnotatedWith(new DescribedPredicate<JavaAnnotation<?>>("@AutoConfigureTestDatabase") {
                @Override
                public boolean test(JavaAnnotation<?> annotation) {
                    return annotation.getRawType().getSimpleName().equals("AutoConfigureTestDatabase");
                }
            });
}
