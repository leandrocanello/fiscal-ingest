rootProject.name = "fiscal-ingest"

include(
    "domain",
    "adapter-fake",
    "adapter-nfe-nacional",
    "infra-postgres",
    "cert-vault",
    "infra-blob-s3",
    // Módulos previstos (CLAUDE.md §19) — habilitar quando o diretório for criado:
    // "ingest-push",
    // "app-worker",
    // "app-api",
)
