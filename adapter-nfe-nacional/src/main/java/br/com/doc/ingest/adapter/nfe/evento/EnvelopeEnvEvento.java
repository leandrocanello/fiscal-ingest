package br.com.doc.ingest.adapter.nfe.evento;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Monta o envelope SOAP 1.2 com o {@code envEvento} de um único evento.
 *
 * <p>Os bytes do evento são concatenados, nunca re-serializados por DOM, o que mudaria o digest. O envelope usa só
 * namespace padrão: o C14N inclusivo leva os namespaces em escopo para o digest, e um {@code xmlns:soap12} no
 * {@code Envelope} invalidaria a assinatura de quem a conferisse no próprio documento.
 */
public final class EnvelopeEnvEvento {

    private static final long ID_LOTE_MAXIMO = 999_999_999_999_999L;

    private EnvelopeEnvEvento() {
    }

    /** @param eventoAssinado {@code evento} assinado em UTF-8, sem declaração XML ({@link EventoManifestacao}) */
    public static byte[] soap12(byte[] eventoAssinado, long idLote) {
        Objects.requireNonNull(eventoAssinado, "eventoAssinado");
        if (idLote < 0 || idLote > ID_LOTE_MAXIMO) {
            throw new IllegalArgumentException("idLote fora de 15 dígitos: " + idLote);
        }
        String antes = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Envelope xmlns=\"" + ContratoRecepcaoEvento.NS_SOAP12 + "\">"
                + "<Body>"
                + "<nfeDadosMsg xmlns=\"" + ContratoRecepcaoEvento.NS_WSDL + "\">"
                + "<envEvento xmlns=\"" + ContratoEvento.NS_NFE + "\" versao=\"" + ContratoRecepcaoEvento.VERSAO_LOTE + "\">"
                + "<idLote>" + String.format("%015d", idLote) + "</idLote>";
        String depois = "</envEvento></nfeDadosMsg></Body></Envelope>";

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.writeBytes(antes.getBytes(StandardCharsets.UTF_8));
        saida.writeBytes(eventoAssinado);
        saida.writeBytes(depois.getBytes(StandardCharsets.UTF_8));
        return saida.toByteArray();
    }
}
