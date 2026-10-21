rootProject.name = "fiscal-ingest"

include(
    "domain",
    "adapter-fake",
    "adapter-nfe-nacional",
    "infra-ledger-postgres",
    "infra-blob-minio",
    "infra-ratelimit-redis",
    "infra-stream-kafka",
    "scheduler",
    "delivery-api",
)
