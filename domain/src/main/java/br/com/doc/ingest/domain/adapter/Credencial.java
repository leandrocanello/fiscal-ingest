package br.com.doc.ingest.domain.adapter;

/**
 * Credencial para autenticar na origem (ex: certificado digital mTLS).
 *
 * <p>Por ora é um placeholder - o formato exato (caminho .pfx + senha, bytes, etc.)
 * sera definido quando implementarmos o adapter real NF-e (Fase 2).
 *
 * @param tipo     tipo de credencial (ex: "MTLS_PFX", "OAUTH")
 * @param caminho  caminho do certificado ou token
 * @param senha    senha do certificado (se aplicavel)
 */
public record Credencial(
    String tipo,
    String caminho,
    char[] senha
) {
    /**
     * Limpa a senha da memoria apos o uso (boa pratica de seguranca).
     */
    public void limparSenha() {
        if (senha != null) {
            java.util.Arrays.fill(senha, '\0');
        }
    }
}
