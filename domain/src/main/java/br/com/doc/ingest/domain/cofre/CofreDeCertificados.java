package br.com.doc.ingest.domain.cofre;

/**
 * Porta do cofre de certificados (CLAUDE.md §7.4): valida e guarda o .pfx só cifrado e
 * o devolve decifrado só em memória, no momento do uso.
 *
 * <p>Falha de negócio volta como resultado tipado, sem segredo e sem causa encadeada
 * (invariante 13).
 */
public interface CofreDeCertificados {

    /** Quem chama continua dono do pedido e deve chamar {@link PedidoGuarda#limpar()} depois. */
    ResultadoGuarda guardar(PedidoGuarda pedido);

    /** Certificado de outro tenant devolve {@link ResultadoAbertura.NaoEncontrado}. */
    ResultadoAbertura abrir(long tenantId, long certificadoId);
}
