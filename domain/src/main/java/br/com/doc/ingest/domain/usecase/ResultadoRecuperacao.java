package br.com.doc.ingest.domain.usecase;

import br.com.doc.ingest.domain.model.Nsu;

import java.util.List;

/**
 * Resultado da recuperacao de gaps (buracos na sequencia de NSUs).
 *
 * @param recuperados  NSUs que foram encontrados e baixados com sucesso
 * @param pendentes    NSUs que nao foram buscados (limite atingido no meio do processo)
 * @param foraDoPrazo  NSUs que a origem indicou como indisponiveis definitivamente
 */
public record ResultadoRecuperacao(
    List<Nsu> recuperados,
    List<Nsu> pendentes,
    List<Nsu> foraDoPrazo
) {
    /** Factory para quando nao ha gaps a recuperar. */
    public static ResultadoRecuperacao vazio() {
        return new ResultadoRecuperacao(List.of(), List.of(), List.of());
    }
}
