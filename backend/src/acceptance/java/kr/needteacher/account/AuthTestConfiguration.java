package kr.needteacher.account;

import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class AuthTestConfiguration {

    @Bean
    @Primary
    Clock authTestClock() {
        return new AdjustableClock();
    }

    @Bean
    GenericContainer<?> authMailpitContainer() {
        return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.6")).withExposedPorts(1025, 8025);
    }

    @Bean
    DynamicPropertyRegistrar authMailProperties(GenericContainer<?> authMailpitContainer) {
        return registry -> {
            registry.add("spring.mail.host", authMailpitContainer::getHost);
            registry.add("spring.mail.port", () -> authMailpitContainer.getMappedPort(1025));
        };
    }
}
