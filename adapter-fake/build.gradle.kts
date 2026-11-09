// adapter-fake
// SourceAdapter em memória para testar o core sem SEFAZ (Fase 1).
// Arquivo recriado na Fase 1.5: não foi recuperado do histórico.
// testFixtures: suíte abstrata de contrato do SourceAdapter (E2-S08, DA-18).

plugins {
    `java-test-fixtures`
}

dependencies {
    implementation(project(":domain"))

    testFixturesApi(project(":domain"))
    testFixturesImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testFixturesImplementation("org.assertj:assertj-core:3.26.0")
}
