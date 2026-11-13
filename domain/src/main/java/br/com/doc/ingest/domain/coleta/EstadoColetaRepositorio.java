package br.com.doc.ingest.domain.coleta;

import br.com.doc.ingest.domain.model.Cnpj;

import java.util.List;
import java.util.Optional;

/**
 * Porta do estado de coleta por {@link ChaveColeta}. A implementação confirma um lote numa
 * única transação: ledger (idempotente) + CAS do cursor (CLAUDE.md §6.3 passo 4, §6.5).
 */
public interface EstadoColetaRepositorio {

    /** Cria o estado com cursor inicial, NSU 0 e versão 0; não faz nada se já existir. */
    void criarSeAusente(ChaveColeta chave);

    Optional<PosicaoColeta> carregar(ChaveColeta chave);

    /**
     * Registra as entradas e avança o cursor se a versão lida ainda for a atual e o NSU não
     * retroceder. Em conflito ou retrocesso, nada é gravado.
     */
    ResultadoCas confirmarLote(ConfirmacaoLote confirmacao);

    /**
     * Troca o certificado da chave por CAS no valor atual ({@code esperado}, nulo se ainda não há).
     * Nunca toca cursor, NSU, versão, lease nem bloqueio: o cursor é do CNPJ consultado (§7.4).
     */
    ResultadoTroca trocarCertificado(ChaveColeta chave, Long esperado, long novo);

    /** Todas as chaves (fontes e ambientes) do CNPJ consultado, com o certificado de cada uma. */
    List<VinculoCertificado> certificadosDoCnpj(Cnpj cnpj);
}
