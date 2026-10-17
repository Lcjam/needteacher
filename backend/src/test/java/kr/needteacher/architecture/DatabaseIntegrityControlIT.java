package kr.needteacher.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManagerFactory;
import kr.needteacher.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Import(TestcontainersConfiguration.class)
class DatabaseIntegrityControlIT {

    private static final String NO_JPA_AUTOCONFIG =
            "spring.autoconfigure.exclude=org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                    + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration";

    @Test
    void jpaExclusionStartsWithoutGuardAndIsRejectedWithGuard() {
        try (ConfigurableApplicationContext context = run(true)) {
            assertTrue(context.isActive());
            assertEquals(0, context.getBeanNamesForType(EntityManagerFactory.class).length);
            assertEquals(1, context.getBeanNamesForType(PostgreSQLContainer.class).length);
        }

        Exception failure = assertThrows(Exception.class, () -> {
            try (ConfigurableApplicationContext context = run(false)) {
                assertTrue(context.isActive());
            }
        });
        assertTrue(causeChainContains(failure, "DB 무결성 위반: EntityManagerFactory 빈 정의가 없습니다"));
    }

    private static ConfigurableApplicationContext run(boolean removeGuard) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(
                        LightweightApplication.class, TestcontainersConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties(NO_JPA_AUTOCONFIG);
        if (removeGuard) {
            builder.sources(RemoveGuard.class);
        }
        return builder.run();
    }

    private static boolean causeChainContains(Throwable failure, String message) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(message)) {
                return true;
            }
        }
        return false;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class LightweightApplication {}

    @Configuration(proxyBeanMethods = false)
    static class RemoveGuard {
        // 통제 컨텍스트에서만 가져온 가드를 제거한다. 나머지 DB 설정은 동일하다.
        @Bean
        static BeanDefinitionRegistryPostProcessor removeGuardDefinition() {
            return new BeanDefinitionRegistryPostProcessor() {
                @Override
                public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
                    String guardName = DatabaseIntegrityGuard.class.getName();
                    if (!registry.containsBeanDefinition(guardName)) {
                        throw new IllegalStateException("Expected imported DatabaseIntegrityGuard definition");
                    }
                    BeanDefinition definition = registry.getBeanDefinition(guardName);
                    if (!guardName.equals(definition.getBeanClassName())) {
                        throw new IllegalStateException("Unexpected DatabaseIntegrityGuard definition");
                    }
                    registry.removeBeanDefinition(guardName);
                }
            };
        }
    }
}
