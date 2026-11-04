package br.com.doc.ingest.domain.catalogo;

import br.com.doc.ingest.domain.model.Cnpj;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Metadado de um documento do catálogo (só metadado, nunca o XML). Campos opcionais ficam vazios
 * quando o arquivo recebido não os traz.
 *
 * @param serie           série
 * @param numero          número da nota
 * @param emissao         data/hora de emissão
 * @param emitenteCnpj    CNPJ do emitente
 * @param destinatarioDoc CNPJ ou CPF do destinatário
 * @param valorTotal      valor total da nota
 * @param versaoLayout    versão do layout do XML
 */
public record MetadadosDocumento(
        Optional<Integer> serie,
        Optional<Long> numero,
        Optional<OffsetDateTime> emissao,
        Optional<Cnpj> emitenteCnpj,
        Optional<String> destinatarioDoc,
        Optional<BigDecimal> valorTotal,
        Optional<String> versaoLayout) {

    public MetadadosDocumento {
        Objects.requireNonNull(serie, "serie");
        Objects.requireNonNull(numero, "numero");
        Objects.requireNonNull(emissao, "emissao");
        Objects.requireNonNull(emitenteCnpj, "emitenteCnpj");
        Objects.requireNonNull(destinatarioDoc, "destinatarioDoc");
        Objects.requireNonNull(valorTotal, "valorTotal");
        Objects.requireNonNull(versaoLayout, "versaoLayout");
        if (serie.filter(s -> s < 0).isPresent()) {
            throw new IllegalArgumentException("série não pode ser negativa");
        }
        if (numero.filter(n -> n < 0).isPresent()) {
            throw new IllegalArgumentException("número não pode ser negativo");
        }
        if (valorTotal.filter(v -> v.signum() < 0).isPresent()) {
            throw new IllegalArgumentException("valor total não pode ser negativo");
        }
        if (destinatarioDoc.filter(d -> !documentoValido(d)).isPresent()) {
            // não ecoa o valor recebido (inv. 13)
            throw new IllegalArgumentException("destinatário deve ter 11 ou 14 dígitos");
        }
    }

    /** Mascara o documento do destinatário (CPF é dado pessoal, invariante 13). */
    @Override
    public String toString() {
        return "MetadadosDocumento[serie=" + serie.map(String::valueOf).orElse("-")
                + ", numero=" + numero.map(String::valueOf).orElse("-")
                + ", emitente=" + emitenteCnpj.map(Cnpj::valor).orElse("-")
                + ", destinatario=" + destinatarioDoc.map(MetadadosDocumento::mascarar).orElse("-") + "]";
    }

    private static boolean documentoValido(String doc) {
        return (doc.length() == 11 || doc.length() == 14) && doc.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    private static String mascarar(String doc) {
        return "***" + doc.substring(doc.length() - 4);
    }
}
