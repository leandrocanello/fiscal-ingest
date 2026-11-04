package br.com.doc.ingest.domain.outbox;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Mensagem do outbox transacional (§8.2): metadado + {@code BlobRef} em JSON já pronto, nunca o XML
 * (invariante 3). A {@code chaveIdempotencia} permite regravar o mesmo lote sem duplicar.
 */
public record MensagemOutbox(String topico, String chave, int versaoSchema,
                             String chaveIdempotencia, String payloadJson) {

    /** Limite do payload em bytes UTF-8: é metadado, não documento. */
    public static final int LIMITE_PAYLOAD_BYTES = 64 * 1024;

    public MensagemOutbox {
        exigirTexto(topico, "topico");
        exigirTexto(chave, "chave");
        exigirTexto(chaveIdempotencia, "chaveIdempotencia");
        exigirTexto(payloadJson, "payloadJson");
        if (versaoSchema < 1) {
            throw new IllegalArgumentException("versaoSchema deve ser >= 1: " + versaoSchema);
        }
        if (payloadJson.getBytes(StandardCharsets.UTF_8).length > LIMITE_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("payload acima de " + LIMITE_PAYLOAD_BYTES + " bytes");
        }
    }

    private static void exigirTexto(String valor, String nome) {
        Objects.requireNonNull(valor, nome);
        if (valor.isBlank()) {
            throw new IllegalArgumentException(nome + " em branco");
        }
    }
}
