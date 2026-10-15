package kr.needteacher;

import kr.needteacher.architecture.DatabaseIntegrityGuard;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** 보호 파일. 모든 통합 테스트가 쓰는 실제 PostgreSQL. 바꾸면 모든 *IT의 DB 검증이 바뀐다. */
@TestConfiguration(proxyBeanMethods = false)
@Import(DatabaseIntegrityGuard.class)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    }
}
