// app-worker — fetcher + processor (CLAUDE.md §19, DA-32).
// Por ora sem Spring: o código de produção só conhece portas do domínio (verificado por ArchUnit).
// Adapter e infra entram só nos testes até existir composição (E3-S08).

dependencies {
    implementation(project(":domain"))

    testImplementation(project(":adapter-fake"))
    // Integração de ponta a ponta (E3-S07): parser real, Postgres real (Testcontainers) e as bases de teste.
    testImplementation(project(":adapter-nfe-nacional"))
    testImplementation(project(":infra-postgres"))
    testImplementation(testFixtures(project(":infra-postgres")))
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
}
