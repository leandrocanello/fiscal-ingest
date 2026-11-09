package br.com.doc.ingest.certvault;

import br.com.doc.ingest.domain.model.Cnpj;

import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Lê o CNPJ do titular no {@code otherName} ICP-Brasil do SAN (DA-36). O CN é texto livre e fica fora.
 *
 * <p>O certificado vem de um upload, então é entrada não confiável (inv. 9): o leitor DER aceita só
 * a forma {@code SEQUENCE { OID, [0] { valor } }}, nunca lança e nunca lê fora do array. Qualquer
 * desvio devolve vazio, e o validador recusa o certificado.
 */
final class ExtratorCnpjCertificado {

    /** {@code 2.16.76.1.3.3} (DOC-ICP-04) codificado em DER. [VALIDAR] em fonte oficial (DA-36). */
    private static final byte[] OID_CNPJ = {0x60, 0x4c, 0x01, 0x03, 0x03};

    private static final int TIPO_OTHER_NAME = 0;
    private static final int TAMANHO_CNPJ = 14;
    private static final int TAG_OCTET_STRING = 0x04;
    private static final int TAG_OID = 0x06;
    private static final int TAG_UTF8_STRING = 0x0c;
    private static final int TAG_PRINTABLE_STRING = 0x13;
    private static final int TAG_SEQUENCE = 0x30;
    private static final int TAG_CONTEXTO_0 = 0xa0;
    private static final int FORMA_LONGA = 0x80;
    private static final int MAX_BYTES_COMPRIMENTO = 2;

    private ExtratorCnpjCertificado() {
    }

    static Optional<Cnpj> extrair(X509Certificate certificado) {
        Collection<List<?>> nomes;
        try {
            nomes = certificado.getSubjectAlternativeNames();
        } catch (CertificateParsingException e) {
            return Optional.empty();
        }
        if (nomes == null) {
            return Optional.empty();
        }
        return nomes.stream()
                .filter(nome -> Integer.valueOf(TIPO_OTHER_NAME).equals(nome.get(0)))
                .filter(nome -> nome.get(1) instanceof byte[])
                .map(nome -> cnpjDoOtherName((byte[]) nome.get(1)))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /** {@code der} é o {@code OtherName} inteiro, como o JDK devolve em {@code getSubjectAlternativeNames}. */
    static Optional<Cnpj> cnpjDoOtherName(byte[] der) {
        Optional<Campo> sequencia = Campo.ler(der, 0, der.length)
                .filter(campo -> campo.tag() == TAG_SEQUENCE && campo.fim() == der.length);
        if (sequencia.isEmpty()) {
            return Optional.empty();
        }
        Optional<Campo> oid = Campo.ler(der, sequencia.get().inicio(), sequencia.get().fim())
                .filter(campo -> campo.tag() == TAG_OID && campo.conteudoIgual(der, OID_CNPJ));
        if (oid.isEmpty()) {
            return Optional.empty();
        }
        Optional<Campo> contexto = Campo.ler(der, oid.get().fim(), sequencia.get().fim())
                .filter(campo -> campo.tag() == TAG_CONTEXTO_0 && campo.fim() == sequencia.get().fim());
        if (contexto.isEmpty()) {
            return Optional.empty();
        }
        return Campo.ler(der, contexto.get().inicio(), contexto.get().fim())
                .filter(campo -> ehTipoDeTexto(campo.tag()) && campo.fim() == contexto.get().fim())
                .flatMap(campo -> cnpjDoValor(der, campo));
    }

    private static boolean ehTipoDeTexto(int tag) {
        return tag == TAG_OCTET_STRING || tag == TAG_PRINTABLE_STRING || tag == TAG_UTF8_STRING;
    }

    private static Optional<Cnpj> cnpjDoValor(byte[] der, Campo valor) {
        if (valor.fim() - valor.inicio() != TAMANHO_CNPJ) {
            return Optional.empty();
        }
        for (int i = valor.inicio(); i < valor.fim(); i++) {
            if (der[i] < '0' || der[i] > '9') {
                return Optional.empty();
            }
        }
        return Optional.of(new Cnpj(new String(der, valor.inicio(), TAMANHO_CNPJ, StandardCharsets.US_ASCII)));
    }

    /** Um TLV do DER: {@code inicio} e {@code fim} delimitam o conteúdo e sempre cabem no limite pedido. */
    private record Campo(int tag, int inicio, int fim) {

        static Optional<Campo> ler(byte[] der, int posicao, int limite) {
            if (limite - posicao < 2) {
                return Optional.empty();
            }
            int tag = der[posicao] & 0xff;
            int primeiro = der[posicao + 1] & 0xff;
            int inicio = posicao + 2;
            int comprimento = primeiro;
            if (primeiro >= FORMA_LONGA) {
                int bytesComprimento = primeiro - FORMA_LONGA;
                if (bytesComprimento == 0 || bytesComprimento > MAX_BYTES_COMPRIMENTO
                        || limite - inicio < bytesComprimento) {
                    return Optional.empty();
                }
                comprimento = 0;
                for (int i = 0; i < bytesComprimento; i++) {
                    comprimento = (comprimento << 8) | (der[inicio + i] & 0xff);
                }
                inicio += bytesComprimento;
            }
            if (comprimento > limite - inicio) {
                return Optional.empty();
            }
            return Optional.of(new Campo(tag, inicio, inicio + comprimento));
        }

        boolean conteudoIgual(byte[] der, byte[] esperado) {
            return Arrays.equals(der, inicio, fim, esperado, 0, esperado.length);
        }
    }
}
