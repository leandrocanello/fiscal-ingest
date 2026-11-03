// cert-vault — CofreDeCertificados local com envelope encryption (CLAUDE.md §7.4, DA-35).
// Só depende do domínio e do JDK (§19): sem Spring, sem log e sem escrita em disco.

dependencies {
    implementation(project(":domain"))

    testImplementation(project(":adapter-fake"))
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
}
