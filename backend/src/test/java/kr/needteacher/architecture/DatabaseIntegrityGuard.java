package kr.needteacher.architecture;

import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 보호 파일. TestcontainersConfiguration을 @Import한 모든 IT 컨텍스트가 시작될 때
 * 실제 PostgreSQL 및 DB 설정 무결성을 검증한다 (T-018 P0, T-021).
 */
public class DatabaseIntegrityGuard
        implements ApplicationContextAware,
                ApplicationListener<ContextRefreshedEvent>,
                BeanPostProcessor,
                BeanFactoryPostProcessor {

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    // 기능 서비스가 JPA 저장소를 주입받으므로, JPA나 Flyway가 빠진 설정은 빈 생성에서 "다른 이유"로 먼저 실패한다.
    // 그래서 빈을 만들기 전에 정의부터 확인한다 (T-051).
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        if (beanFactory.getBeanNamesForType(EntityManagerFactory.class, true, false).length == 0) {
            throw new IllegalStateException("DB 무결성 위반: EntityManagerFactory 빈 정의가 없습니다");
        }
        if (beanFactory.getBeanNamesForType(Flyway.class, true, false).length == 0) {
            throw new IllegalStateException("DB 무결성 위반: Flyway 빈 정의가 없습니다");
        }
    }

    // 엔티티가 생기면 Flyway가 빠진 설정은 Hibernate 스키마 검증에서 먼저 터진다.
    // 그 전에 Flyway를 확인해야 우회 설정이 "다른 이유"로 숨지 않는다 (T-050).
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof LocalContainerEntityManagerFactoryBean) {
            requireMigratedFlyway(applicationContext);
        }
        return bean;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        if (context != applicationContext) {
            return; // 자식 컨텍스트 이벤트는 부모에도 전파된다
        }

        String[] containerBeans = context.getBeanNamesForType(PostgreSQLContainer.class);
        if (containerBeans.length == 0) {
            throw new IllegalStateException("DB 무결성 위반: PostgreSQLContainer 빈이 없습니다");
        }
        PostgreSQLContainer container = context.getBean(PostgreSQLContainer.class);
        String containerUrl = container.getJdbcUrl();

        Map<String, DataSource> dataSources = context.getBeansOfType(DataSource.class);
        if (dataSources.isEmpty()) {
            throw new IllegalStateException("DB 무결성 위반: DataSource 빈이 없습니다");
        }
        for (Map.Entry<String, DataSource> bean : dataSources.entrySet()) {
            try (Connection conn = bean.getValue().getConnection()) {
                String productName = conn.getMetaData().getDatabaseProductName();
                if (!"PostgreSQL".equals(productName)) {
                    throw new IllegalStateException(
                            "DB 무결성 위반: DataSource " + bean.getKey() + " 제품명이 PostgreSQL이 아닙니다 (" + productName + ")");
                }
                String dsUrl = conn.getMetaData().getURL();
                if (!containerUrl.equals(dsUrl)) {
                    throw new IllegalStateException("DB 무결성 위반: DataSource " + bean.getKey() + " URL(" + dsUrl
                            + ")이 컨테이너 URL(" + containerUrl + ")과 다릅니다");
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("DB 무결성 위반: DataSource " + bean.getKey() + " 연결 검증 실패", e);
            }
        }

        Flyway flyway = requireMigratedFlyway(context);
        DataSource flywayDs = flyway.getConfiguration().getDataSource();
        if (flywayDs == null) {
            throw new IllegalStateException("DB 무결성 위반: Flyway DataSource가 없습니다");
        }
        try (Connection conn = flywayDs.getConnection()) {
            String flywayUrl = conn.getMetaData().getURL();
            if (!containerUrl.equals(flywayUrl)) {
                throw new IllegalStateException(
                        "DB 무결성 위반: Flyway URL(" + flywayUrl + ")이 컨테이너 URL(" + containerUrl + ")과 다릅니다");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("DB 무결성 위반: Flyway 연결 검증 실패", e);
        }

        Map<String, EntityManagerFactory> entityManagerFactories = context.getBeansOfType(EntityManagerFactory.class);
        if (entityManagerFactories.isEmpty()) {
            throw new IllegalStateException("DB 무결성 위반: EntityManagerFactory 빈이 없습니다");
        }
        for (Map.Entry<String, EntityManagerFactory> bean : entityManagerFactories.entrySet()) {
            Map<String, Object> props = bean.getValue().getProperties();
            Object ddlAuto = props.get("hibernate.hbm2ddl.auto");
            if (!"validate".equals(ddlAuto)) {
                throw new IllegalStateException("DB 무결성 위반: EntityManagerFactory " + bean.getKey()
                        + " hibernate.hbm2ddl.auto가 validate가 아닙니다 (" + ddlAuto + ")");
            }
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                String key = entry.getKey();
                if (key != null && key.contains("schema-generation.database.action")) {
                    Object val = entry.getValue();
                    if (val != null && !"none".equalsIgnoreCase(val.toString().trim())) {
                        throw new IllegalStateException("DB 무결성 위반: " + key + " 속성이 none이 아닙니다 (" + val + ")");
                    }
                }
            }
        }
    }

    private static Flyway requireMigratedFlyway(ApplicationContext context) {
        if (context.getBeanNamesForType(Flyway.class).length == 0) {
            throw new IllegalStateException("DB 무결성 위반: Flyway 빈이 없습니다");
        }
        Flyway flyway = context.getBean(Flyway.class);
        Location[] locations = flyway.getConfiguration().getLocations();
        if (locations == null
                || locations.length != 1
                || !"classpath:db/migration".equals(locations[0].getDescriptor())) {
            throw new IllegalStateException("DB 무결성 위반: Flyway locations(" + Arrays.toString(locations)
                    + ")가 기본값(classpath:db/migration)이 아닙니다");
        }
        for (MigrationInfo migration : flyway.info().all()) {
            if (!migration.getState().isApplied()) {
                throw new IllegalStateException(
                        "DB 무결성 위반: Flyway 마이그레이션 " + migration.getVersion() + " 상태가 " + migration.getState() + "입니다");
            }
        }
        return flyway;
    }
}
