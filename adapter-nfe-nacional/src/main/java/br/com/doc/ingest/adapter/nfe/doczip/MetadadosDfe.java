package br.com.doc.ingest.adapter.nfe.doczip;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Metadado extraído do XML de um DF-e (só metadado, nunca o XML). Campos opcionais ficam vazios
 * quando o schema não os traz.
 *
 * @param chave                 chave de acesso (44 dígitos)
 * @param modelo                modelo derivado das posições 21–22 da chave
 * @param cnpjEmitente          CNPJ do emitente
 * @param documentoDestinatario CNPJ ou CPF do destinatário
 * @param cnpjsAutorizados      CNPJs de {@code autXML}
 * @param cnpjTransportador     CNPJ do transportador
 * @param serie                 série
 * @param numero                número da nota
 * @param emissao               data/hora de emissão
 * @param valorTotal            valor total da nota
 * @param tipoEvento            código do evento (ex.: 210210)
 * @param seqEvento             sequência do evento
 * @param situacao              {@code cSitNFe} do resumo
 * @param tipoOperacao          {@code tpNF} (0 entrada, 1 saída)
 * @param cnpjAutorEvento       CNPJ do autor do evento (só em eventos)
 */
public record MetadadosDfe(
        String chave,
        String modelo,
        Optional<String> cnpjEmitente,
        Optional<String> documentoDestinatario,
        List<String> cnpjsAutorizados,
        Optional<String> cnpjTransportador,
        Optional<Integer> serie,
        Optional<Long> numero,
        Optional<OffsetDateTime> emissao,
        Optional<BigDecimal> valorTotal,
        Optional<String> tipoEvento,
        Optional<Integer> seqEvento,
        Optional<String> situacao,
        Optional<String> tipoOperacao,
        Optional<String> cnpjAutorEvento) {

    public MetadadosDfe {
        cnpjsAutorizados = List.copyOf(cnpjsAutorizados);
    }

    /** Mascara o documento do destinatário (CPF é dado pessoal, invariante 13). */
    @Override
    public String toString() {
        return "MetadadosDfe[modelo=" + modelo + ", emitente=" + cnpjEmitente.orElse("-")
                + ", destinatario=" + documentoDestinatario.map(MetadadosDfe::mascarar).orElse("-") + "]";
    }

    private static String mascarar(String doc) {
        return doc.length() <= 4 ? "***" : "***" + doc.substring(doc.length() - 4);
    }
}
