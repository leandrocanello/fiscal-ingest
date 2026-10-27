// adapter-nfe-nacional
// Adapter real para NF-e Ambiente Nacional (SOAP distNSU/consNSU + manifestacao)
// Usa `java.net.http` (HTTP/mTLS) + JAXB jakarta + Santuario (XML-DSig) + WireMock (testes)
// (DECISIONS.md: "Libs SOAP fiscais" — sem Apache CXF/javax)

dependencies {
    implementation(project(":domain"))

    // Apache Santuario - assinatura XML (xmldsig)
    implementation("org.apache.santuario:xmlsec:3.0.4")

    // JAXB - bind XML <-> Java
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.1")
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.4")

    // Log (ADR-028; DA-8) - so a API aqui; o binding fica com os app-*
    implementation("org.slf4j:slf4j-api:2.0.16")

    // Testes - WireMock para simular a SEFAZ
    testImplementation("org.wiremock:wiremock:3.9.1")

    // Logback em teste para capturar o WARN do fallback (ListAppender)
    testImplementation("ch.qos.logback:logback-classic:1.5.12")
}
