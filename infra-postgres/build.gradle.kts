// infra-postgres — persistência em Postgres com JDBI 3 + Flyway (ADR-025).
// Sem Spring e sem JPA (ADR-027): verificado por ArchUnit.

dependencies {
    implementation(project(":domain"))

    implementation("org.jdbi:jdbi3-core:3.45.4")
    implementation("org.jdbi:jdbi3-postgres:3.45.4")
    implementation("org.flywaydb:flyway-core:10.17.3")
    implementation("org.flywaydb:flyway-database-postgresql:10.17.3")
    implementation("org.postgresql:postgresql:42.7.4")

    // Testcontainers 2.x: compatível com Docker Engine 29 (DA-11)
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
}
