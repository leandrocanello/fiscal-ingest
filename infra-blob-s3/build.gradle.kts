// infra-blob-s3 — BlobStore sobre API S3 compatível (AWS SDK v2). Sem Spring (ADR-027).

dependencies {
    implementation(project(":domain"))

    implementation(platform("software.amazon.awssdk:bom:2.29.52"))
    implementation("software.amazon.awssdk:s3") {
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
        exclude(group = "software.amazon.awssdk", module = "apache-client")
    }
    implementation("software.amazon.awssdk:url-connection-client")

    testImplementation(testFixtures(project(":adapter-fake")))
    testImplementation(project(":adapter-fake"))

    // Testcontainers 2.x (DA-11)
    testImplementation("org.testcontainers:testcontainers:2.0.2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
}
