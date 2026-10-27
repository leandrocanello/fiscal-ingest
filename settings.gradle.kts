rootProject.name = "fiscal-ingest"

include(
    "domain",
    "adapter-fake",
    "adapter-nfe-nacional",
    "infra-postgres",
    // Módulos previstos (CLAUDE.md §19) — habilitar quando o diretório for criado:
    // "ingest-push",
    // "cert-vault",
    // "infra-blob-minio",
    // "app-worker",
    // "app-api",
)
