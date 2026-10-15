package kr.needteacher.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.SecurityFilterChain;

/** 보호 파일. PIT 면제 설정 클래스의 무결성을 검증한다 (T-047, T-048). */
@AnalyzeClasses(packages = "kr.needteacher", importOptions = ImportOption.DoNotIncludeTests.class)
class ConfigurationIntegrityTest {

    private static final String EXEMPT_CLASS = "kr.needteacher.security.SecurityConfig";
    private static final String EXEMPT_SOURCE_FILE = "SecurityConfig.java";

    @ArchTest
    static void securityConfigIntegrity(JavaClasses classes) {
        JavaClass configClass = classes.get(EXEMPT_CLASS);
        assertThat(configClass).as("면제 설정 클래스가 존재해야 한다").isNotNull();
        assertThat(configClass.isAnnotatedWith(Configuration.class))
                .as("@Configuration 어노테이션이 붙어 있어야 한다")
                .isTrue();

        // 1. 소스 파일에서 생성된 운영 클래스는 정확히 SecurityConfig 하나뿐이어야 한다 (중첩·익명·추가 최상위 클래스 금지)
        Set<String> classesInSourceFile = classes.stream()
                .filter(c -> EXEMPT_SOURCE_FILE.equals(c.getSourceCodeLocation().getSourceFileName()))
                .map(JavaClass::getName)
                .collect(Collectors.toSet());
        assertThat(classesInSourceFile)
                .as("SecurityConfig.java 파일에는 정확히 SecurityConfig 클래스 하나만 존재해야 한다")
                .containsExactly(EXEMPT_CLASS);

        assertThat(configClass.isNestedClass()).as("중첩 클래스가 아니어야 한다").isFalse();

        // 2. 필드 0개
        assertThat(configClass.getFields()).as("필드가 없어야 한다").isEmpty();

        // 3. 무인자 생성자 1개만
        assertThat(configClass.getConstructors()).as("생성자는 정확히 1개여야 한다").hasSize(1);
        JavaConstructor ctor = configClass.getConstructors().iterator().next();
        assertThat(ctor.getRawParameterTypes()).as("생성자는 무인자여야 한다").isEmpty();

        // 4. 비 synthetic 선언 메서드는 정확히 @Bean SecurityFilterChain securityFilterChain 하나만
        List<JavaMethod> declaredMethods = configClass.getMethods().stream()
                .filter(m -> !m.getModifiers().contains(JavaModifier.SYNTHETIC))
                .toList();

        assertThat(declaredMethods).as("비 synthetic 선언 메서드는 정확히 1개여야 한다").hasSize(1);

        JavaMethod beanMethod = declaredMethods.get(0);
        assertThat(beanMethod.getName()).isEqualTo("securityFilterChain");
        assertThat(beanMethod.isAnnotatedWith(Bean.class))
                .as("@Bean 어노테이션이 붙어 있어야 한다")
                .isTrue();
        assertThat(beanMethod.getRawReturnType().isAssignableTo(SecurityFilterChain.class))
                .as("SecurityFilterChain을 반환해야 한다")
                .isTrue();
    }
}
