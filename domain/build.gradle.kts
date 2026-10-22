// Módulo DOMÍNIO — deve permanecer PURO (sem I/O, sem framework).
// Nenhuma dependência além do JDK. Nem mesmo Jackson/SLF4J/JDBC.
// A pureza é verificada por ArchUnit (domain/src/test/.../arquitetura/RegrasDoDominio).
// Esta restrição é intencional e faz parte da arquitetura (ver docs/ARCHITECTURE.md §4.1).
dependencies {
    val testImplementation by configurations
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")

    // Intencionalmente vazio nas dependências de produção.
    // Se você sentir necessidade de adicionar algo aqui, provavelmente
    // a lógica pertence a um adapter ou módulo de infra.
}
