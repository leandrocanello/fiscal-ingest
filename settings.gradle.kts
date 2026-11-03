rootProject.name = "fiscal-ingest"

include(
    "domain",
    "adapter-fake",
    "adapter-nfe-nacional",
    "infra-postgres",
    "cert-vault",
    // Módulos previstos (CLAUDE.md §19) — habilitar quando o diretório for criado:
    // "ingest-push",
    // "infra-blob-minio",
    // "app-worker",
    // "app-api",
)
