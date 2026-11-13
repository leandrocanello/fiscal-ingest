// cert-vault — CofreDeCertificados local com envelope encryption (CLAUDE.md §7.4, DA-35).
// Só depende do domínio e do JDK (§19): sem Spring, sem log e sem escrita em disco.
// testFixtures: cadeia ICP sintética gerada em memória, relógio e repositório de teste (DA-35).

plugins {
    `java-test-fixtures`
}

dependencies {
    implementation(project(":domain"))

    testFixturesApi(project(":domain"))
    // BouncyCastle só para gerar certificados e .pfx sintéticos; fica fora do código de produção
    testFixturesImplementation("org.bouncycastle:bcpkix-jdk18on:1.79")

    testImplementation(testFixtures(project(":domain")))
    testImplementation(project(":adapter-fake"))
    // CarregadorPfx do adapter: a credencial aberta pelo cofre precisa carregar no consumidor real
    testImplementation(project(":adapter-nfe-nacional"))
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
}
