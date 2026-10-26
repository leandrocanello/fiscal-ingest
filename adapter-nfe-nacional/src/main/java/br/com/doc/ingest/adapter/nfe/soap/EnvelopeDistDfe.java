package br.com.doc.ingest.adapter.nfe.soap;

import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.Nsu;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Monta o corpo {@code distDFeInt} da consulta por ultNSU. */
public final class EnvelopeDistDfe {

    static final String VERSAO_LAYOUT = "1.01";

    private static final JAXBContext CONTEXTO = criarContexto();

    private EnvelopeDistDfe() {
    }

    private static JAXBContext criarContexto() {
        try {
            return JAXBContext.newInstance(DistDfeInt.class);
        } catch (JAXBException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** Fragmento XML (sem declaração), UTF-8. O ultNSU vai com 15 dígitos. */
    public static byte[] distNsu(TipoAmbiente ambiente, Cnpj cnpj, Nsu ultNsu) {
        DistDfeInt req = new DistDfeInt();
        req.setVersao(VERSAO_LAYOUT);
        req.setTpAmb(ambiente.codigoTpAmb());
        req.setCnpj(cnpj.valor());
        req.setUltNsu(String.format("%015d", ultNsu.valor()));
        try {
            Marshaller m = CONTEXTO.createMarshaller();
            m.setProperty(Marshaller.JAXB_FRAGMENT, true);
            m.setProperty(Marshaller.JAXB_ENCODING, StandardCharsets.UTF_8.name());
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            m.marshal(req, saida);
            return saida.toByteArray();
        } catch (JAXBException e) {
            throw new IllegalStateException("Falha ao montar distDFeInt", e);
        }
    }
}
