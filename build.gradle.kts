// Build raiz — configuração comum a todos os subprojetos
plugins {
    java
}

allprojects {
    group = "br.com.doc"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    dependencies {
        val implementation by configurations
        val testImplementation by configurations
        val testRuntimeOnly by configurations

        testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
        testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
        testImplementation("org.assertj:assertj-core:3.26.0")
        testImplementation("net.jqwik:jqwik:1.9.3")
        testImplementation("org.mockito:mockito-core:5.12.0")
        testImplementation("org.mockito:mockito-junit-jupiter:5.12.0")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
