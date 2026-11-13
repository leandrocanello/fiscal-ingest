package br.com.doc.ingest.adapter.nfe;

import br.com.doc.ingest.domain.model.ChaveAcesso;

/**
 * Chave de acesso que o adapter envia à SEFAZ: formato da NT Conjunta 2025.001 (CNPJ do emitente
 * alfanumérico) e DV obrigatório, para não gastar consulta num pedido que seria rejeitado (DA-38).
 */
public record ChaveAcessoNfe(String valor) {

    public ChaveAcessoNfe {
        if (!valida(valor)) {
            // não ecoa o valor recebido (inv. 13)
            throw new IllegalArgumentException(
                    "chave de acesso inválida: esperado o formato da NT 2025.001 com DV certo");
        }
    }

    /** Formato da NT e DV de módulo 11 alfanumérico, sem lançar exceção. */
    public static boolean valida(String valor) {
        return ChaveAcesso.formatoValido(valor) && new ChaveAcesso(valor).temDvValido();
    }

    @Override
    public String toString() {
        return "ChaveAcessoNfe[***]";
    }
}
