package br.com.doc.ingest.domain.catalogo;

import br.com.doc.ingest.domain.model.Cnpj;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Documentos de participantes lidos de dentro do XML (inv. 10). No evento, {@code emitente} é o CNPJ
 * lido da chave de acesso, nunca o autor do evento.
 *
 * @param emitente      CNPJ do emitente
 * @param destinatario  CNPJ ou CPF do destinatário (dado pessoal: fora do {@code toString})
 * @param transportador CNPJ do transportador
 * @param autorizados   CNPJs do grupo {@code autXML}
 */
public record CnpjsDoConteudo(
        Optional<Cnpj> emitente,
        Optional<String> destinatario,
        Optional<Cnpj> transportador,
        List<Cnpj> autorizados) {

    public CnpjsDoConteudo {
        Objects.requireNonNull(emitente, "emitente");
        Objects.requireNonNull(destinatario, "destinatario");
        Objects.requireNonNull(transportador, "transportador");
        autorizados = List.copyOf(autorizados);
    }

    @Override
    public String toString() {
        return "CnpjsDoConteudo[emitente=" + emitente
                + ", destinatario=" + destinatario.map(d -> "***").orElse("-")
                + ", transportador=" + transportador
                + ", autorizados=" + autorizados + "]";
    }
}
