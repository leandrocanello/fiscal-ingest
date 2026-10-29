package br.com.doc.ingest.adapter.nfe.soap;

import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.Nsu;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Monta o corpo {@code distDFeInt} da consulta por ultNSU. */
public final class EnvelopeDistDfe {

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
        DistDfeInt req = base(ambiente, cnpj);
        req.setUltNsu(nsu15(ultNsu));
        return marshal(req);
    }

    /** Consulta pontual por NSU ({@code consNSU}); nunca leva distNSU. [VALIDAR item 9] nomes dos elementos. */
    public static byte[] consNsu(TipoAmbiente ambiente, Cnpj cnpj, Nsu nsu) {
        DistDfeInt req = base(ambiente, cnpj);
        req.setNsuPontual(nsu15(nsu));
        return marshal(req);
    }

    /** Consulta por chave de acesso ({@code consChNFe}); nunca leva distNSU. [VALIDAR item 9] nomes dos elementos. */
    public static byte[] consChNFe(TipoAmbiente ambiente, Cnpj cnpj, String chave) {
        DistDfeInt req = base(ambiente, cnpj);
        req.setChaveNFe(chave);
        return marshal(req);
    }

    private static DistDfeInt base(TipoAmbiente ambiente, Cnpj cnpj) {
        DistDfeInt req = new DistDfeInt();
        req.setVersao(ContratoDistDfe.VERSAO_LAYOUT);
        req.setTpAmb(ambiente.codigoTpAmb());
        req.setCnpj(cnpj.valor());
        return req;
    }

    private static String nsu15(Nsu nsu) {
        return String.format(Locale.ROOT, "%015d", nsu.valor());
    }

    private static byte[] marshal(DistDfeInt req) {
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

    /** Envelope SOAP 1.2 completo (UTF-8, com prólogo) em volta do distDFeInt. */
    public static byte[] soap12DistNsu(TipoAmbiente ambiente, Cnpj cnpj, Nsu ultNsu) {
        return soap12(distNsu(ambiente, cnpj, ultNsu));
    }

    public static byte[] soap12ConsNsu(TipoAmbiente ambiente, Cnpj cnpj, Nsu nsu) {
        return soap12(consNsu(ambiente, cnpj, nsu));
    }

    public static byte[] soap12ConsChNFe(TipoAmbiente ambiente, Cnpj cnpj, String chave) {
        return soap12(consChNFe(ambiente, cnpj, chave));
    }

    private static byte[] soap12(byte[] corpoXml) {
        String corpo = new String(corpoXml, StandardCharsets.UTF_8);
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap12:Envelope xmlns:soap12=\"" + ContratoDistDfe.NS_SOAP12 + "\">"
                + "<soap12:Body>"
                + "<" + ContratoDistDfe.OPERACAO + " xmlns=\"" + ContratoDistDfe.NS_WSDL + "\">"
                + "<nfeDadosMsg>" + corpo + "</nfeDadosMsg>"
                + "</" + ContratoDistDfe.OPERACAO + ">"
                + "</soap12:Body></soap12:Envelope>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }
}
