// app-worker — fetcher + processor (CLAUDE.md §19, DA-32).
// Por ora sem Spring: o código de produção só conhece portas do domínio (verificado por ArchUnit).
// Adapter e infra entram só nos testes até existir composição (E3-S08).

dependencies {
    implementation(project(":domain"))

    testImplementation(project(":adapter-fake"))
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
}
