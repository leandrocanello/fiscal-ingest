// Módulo DOMÍNIO — deve permanecer PURO (sem I/O, sem framework).
// Nenhuma dependência além do JDK. Nem mesmo Jackson/SLF4J/JDBC.
// Esta restrição é intencional e faz parte da arquitetura (ver docs/ARCHITECTURE.md §4.1).
dependencies {
    // Intencionalmente vazio nas dependências de produção.
    // Se você sentir necessidade de adicionar algo aqui, provavelmente
    // a lógica pertence a um adapter ou módulo de infra.
}
