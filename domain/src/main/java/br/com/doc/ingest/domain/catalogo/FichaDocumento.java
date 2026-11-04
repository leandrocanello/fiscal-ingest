package br.com.doc.ingest.domain.catalogo;

import br.com.doc.ingest.domain.model.ChaveAcesso;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Tudo o que o catálogo sabe de um documento: só metadado e {@code BlobRef}, nunca o XML (inv. 3).
 *
 * @param documentoId  identificador do documento
 * @param tenantId     tenant dono
 * @param chave        chave de acesso
 * @param estado       estado fundido dos registros
 * @param vinculos     empresas do tenant vinculadas; vazio é o caso {@code SEM_EMPRESA} da §7.3
 * @param arquivos     arquivos do documento, com suas proveniências
 * @param atualizadoEm última mudança do documento
 */
public record FichaDocumento(
        long documentoId,
        long tenantId,
        ChaveAcesso chave,
        EstadoDocumento estado,
        List<VinculoEmpresa> vinculos,
        List<ArquivoFicha> arquivos,
        Instant atualizadoEm) {

    public FichaDocumento {
        Objects.requireNonNull(chave, "chave");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(vinculos, "vinculos");
        Objects.requireNonNull(arquivos, "arquivos");
        Objects.requireNonNull(atualizadoEm, "atualizadoEm");
        vinculos = List.copyOf(vinculos);
        arquivos = List.copyOf(arquivos);
    }
}
