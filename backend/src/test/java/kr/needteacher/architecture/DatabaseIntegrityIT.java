package kr.needteacher.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import javax.sql.DataSource;
import kr.needteacher.BackendApplication;
import kr.needteacher.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 보호 파일. 설정 파일을 글자로 스캔하는 방식은 인라인 맵·이스케이프·프로필로 피할 수 있다 (T-015).
 * 그래서 Spring이 실제로 적용한 결과를 런타임에 확인한다: 실제 PostgreSQL, Flyway 동작, 스키마는 검증만.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DatabaseIntegrityIT {

    private static final String NO_JPA_AUTOCONFIG =
            "spring.autoconfigure.exclude=org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                    + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration";

    @Autowired
    DataSource dataSource;

    // Flyway가 꺼져 있으면 이 빈이 없어서 테스트가 시작조차 못 한다
    @Autowired
    Flyway flyway;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    PostgreSQLContainer postgresContainer;

    @Test
    void applicationRunsOnRealPostgres() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
        }
    }

    @Test
    void flywayMigratesTheSameDatabase() throws Exception {
        try (Connection application = dataSource.getConnection();
                Connection migration = flyway.getConfiguration().getDataSource().getConnection()) {
            assertEquals("PostgreSQL", migration.getMetaData().getDatabaseProductName());
            assertEquals(
                    application.getMetaData().getURL(), migration.getMetaData().getURL());
        }
        assertEquals(0, flyway.info().pending().length);
    }

    @Test
    void hibernateOnlyValidatesSchema() {
        assertEquals("validate", entityManagerFactory.getProperties().get("hibernate.hbm2ddl.auto"));
    }

    @ParameterizedTest(name = "rejects {0}")
    @ValueSource(
            strings = {
                "spring.jpa.properties.hibernate.hbm2ddl.auto=create",
                "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=drop-and-create",
                "spring.flyway.enabled=false",
                "spring.flyway.locations=classpath:nowhere",
                NO_JPA_AUTOCONFIG,
                "spring.autoconfigure.exclude=org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
            })
    void rejectsDatabaseIntegrityOverrides(String property) {
        Exception failure = assertThrows(Exception.class, () -> {
            try (var context = new SpringApplicationBuilder(BackendApplication.class, TestcontainersConfiguration.class)
                    .web(WebApplicationType.NONE)
                    .properties(property)
                    .run()) {
                assertTrue(context.isActive());
            }
        });
        assertTrue(causeChainContains(failure, "DB 무결성 위반"), () -> property + " failed for another reason: " + failure);
    }

    private static boolean causeChainContains(Throwable failure, String message) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(message)) {
                return true;
            }
        }
        return false;
    }
}
