// adapter-nfe-nacional
// Adapter real para NF-e Ambiente Nacional (SOAP distNSU/consNSU + manifestacao)
// Usa Apache CXF (cliente SOAP) + Santuario (assinatura XML) + WireMock (testes)

dependencies {
    implementation(project(":domain"))

    // Apache CXF - cliente SOAP + WS-Security
    implementation("org.apache.cxf:cxf-rt-frontend-jaxws:3.5.5")
    implementation("org.apache.cxf:cxf-rt-transports-http:3.5.5")
    implementation("org.apache.cxf:cxf-rt-ws-security:3.5.5")

    // Apache Santuario - assinatura XML (xmldsig)
    implementation("org.apache.santuario:xmlsec:3.0.4")

    // JAXB - bind XML <-> Java
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.1")
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.4")

    // Testes - WireMock para simular a SEFAZ
    testImplementation("org.wiremock:wiremock:3.9.1")
}
