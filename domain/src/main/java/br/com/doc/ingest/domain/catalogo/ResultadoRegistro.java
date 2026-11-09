package br.com.doc.ingest.domain.catalogo;

import java.util.List;
import java.util.Objects;

/**
 * O que um registro mudou no catálogo; é com isso que quem chama decide o que publicar.
 *
 * @param documentoId       documento canônico
 * @param arquivoId         arquivo registrado
 * @param documentoCriado   o documento nasceu neste registro
 * @param documentoAlterado o estado de um documento já existente mudou
 * @param arquivoNovo       o arquivo não existia no documento
 * @param provenienciaNova  a proveniência não existia no arquivo
 * @param vinculosNovos     vínculos que não existiam no documento
 */
public record ResultadoRegistro(
        long documentoId,
        long arquivoId,
        boolean documentoCriado,
        boolean documentoAlterado,
        boolean arquivoNovo,
        boolean provenienciaNova,
        List<VinculoEmpresa> vinculosNovos) {

    public ResultadoRegistro {
        Objects.requireNonNull(vinculosNovos, "vinculosNovos");
        vinculosNovos = List.copyOf(vinculosNovos);
    }
}
