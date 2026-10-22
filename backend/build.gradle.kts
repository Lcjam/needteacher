// 보호 파일. 품질 기준은 docs/proposals/test-harness.md 2절. 기준치는 올릴 수만 있다(래칫).
plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.10.4"
    id("info.solidsoft.pitest") version "1.19.0"
}

group = "kr.needteacher"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// 경고 = 에러
tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing", "-Werror"))
}

// 형식: palantir-java-format(4칸). check에 포함된다. 고치기: bash ./gradlew spotlessApply
spotless {
    java {
        palantirJavaFormat("2.102.0")
    }
}

// 단위(*Test)와 통합(*IT, Testcontainers)을 나눠 돌린다. 어느 쪽이든 찾은 테스트가 0개면 실패
tasks.withType<Test> {
    useJUnitPlatform()
    failOnNoDiscoveredTests = true
}
tasks.test {
    filter { excludeTestsMatching("*IT") }
}
val integrationTest = tasks.register<Test>("integrationTest") {
    description = "통합 테스트(*IT, Testcontainers)"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("*IT") }
    // ArchUnit 엔진은 클래스명 필터를 무시한다. 빼지 않으면 IT가 0개여도 ArchUnit 4건으로 "0건 금지"를 통과한다
    useJUnitPlatform { excludeEngines("archunit") }
    shouldRunAfter(tasks.test)
}

// 커버리지: 단위+통합 합산
jacoco {
    toolVersion = "0.8.15"
}
val measuredClasses = sourceSets.main.get().output.classesDirs.asFileTree.matching {
    exclude("kr/needteacher/BackendApplication.class")
}
tasks.jacocoTestReport {
    dependsOn(tasks.test, integrationTest)
    executionData(tasks.test.get(), integrationTest.get())
    classDirectories.setFrom(measuredClasses)
    reports {
        xml.required = true
        html.required = true
    }
}
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    executionData(tasks.test.get(), integrationTest.get())
    classDirectories.setFrom(measuredClasses)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}
tasks.check {
    dependsOn(integrationTest, tasks.jacocoTestCoverageVerification)
}

// 뮤테이션: -Pfull이면 check에 포함. verify.sh fast는 변경 클래스만 -PpitTargetClasses로 돌린다.
// 기준치는 여기 고정이라 명령행으로 못 낮춘다.
pitest {
    pitestVersion = "1.30.0"
    junit5PluginVersion = "1.2.3"
    // full은 대상 지정을 무시한다 (환경 변수·전역 gradle.properties로 범위를 좁히지 못하게)
    targetClasses.set(
        if (providers.gradleProperty("full").isPresent) setOf("kr.needteacher.*")
        else providers.gradleProperty("pitTargetClasses").map { it.split(",").toSet() }.orElse(setOf("kr.needteacher.*")).get())
    // targetTests 기본값은 targetClasses라서, 변경 클래스만 지정하면 테스트도 같은 이름으로 좁혀져 0건이 된다
    targetTests.set(setOf("kr.needteacher.*"))
    excludedClasses.set(setOf("kr.needteacher.BackendApplication", "kr.needteacher.security.SecurityConfig"))
    excludedTestClasses.set(setOf("*IT"))
    // DEFAULTS보다 많은 변이(조건 제거 등). 같은 호출 비교·assertNotNull·any()만 하는 약한 테스트를 생존 뮤턴트로 드러낸다 (T-013)
    mutators.set(setOf("STRONGER"))
    mutationThreshold.set(80)
    failWhenNoMutations.set(true)
    outputFormats.set(setOf("XML", "HTML"))
    timestampedReports.set(false)
    useClasspathFile.set(true)
    // 플러그인이 넣는 런처(1.x)가 Spring BOM의 JUnit 6과 충돌한다. 런처는 testRuntimeOnly(BOM 버전)를 쓴다.
    addJUnitPlatformLauncher.set(false)
}
if (providers.gradleProperty("full").isPresent) {
    tasks.check {
        dependsOn(tasks.pitest)
    }
}
