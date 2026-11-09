package br.com.doc.ingest.adapter.nfe.evento;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lê o corpo HTTP da resposta do NFeRecepcaoEvento pelo {@link FabricaDocumentoSeguro}: {@code retEnvEvento} é achado
 * em qualquer nível do {@code Body} [VALIDAR FONTES-OFICIAIS item 9: nome do wrapper da resposta].
 *
 * <p>Os campos são lidos só entre filhos diretos: o {@code cStat} do lote e o do evento têm o mesmo nome e um não pode
 * ocupar o lugar do outro. O {@code xMotivo} não é lido, e nenhuma falha ou {@code toString} ecoa a resposta (inv. 13).
 */
public final class LeitorRetEnvEvento {

    private LeitorRetEnvEvento() {
    }

    public sealed interface Resultado {
    }

    public record Valida(String cStatLote, Optional<RetornoEvento> retornoEvento) implements Resultado {
        @Override
        public String toString() {
            return "Valida[retEvento=" + retornoEvento.isPresent() + "]";
        }
    }

    /** {@code motivo} é um texto fixo e curto, nunca trecho da resposta. */
    public record Invalida(String motivo) implements Resultado {
    }

    /** Evento rejeitado pode vir sem chave, tipo, sequência ou protocolo; só o {@code cStat} é obrigatório. */
    public record RetornoEvento(String cStat, Optional<String> nProt, Optional<String> chaveAcesso,
                                Optional<String> tpEvento, Optional<String> nSeqEvento) {
        @Override
        public String toString() {
            return "RetornoEvento[protocolo=" + nProt.isPresent() + "]";
        }
    }

    public static Resultado ler(byte[] corpo) {
        Document doc;
        try {
            doc = FabricaDocumentoSeguro.ler(corpo);
        } catch (XmlInseguroOuInvalidoException e) {
            return new Invalida("corpo não é XML bem formado ou contém DOCTYPE");
        }

        Element raiz = doc.getDocumentElement();
        if (!ehSoap12(raiz, "Envelope")) {
            return new Invalida("resposta não é envelope SOAP 1.2");
        }
        Element body = filhos(raiz, ContratoRecepcaoEvento.NS_SOAP12, "Body").stream().findFirst().orElse(null);
        if (body == null) {
            return new Invalida("envelope SOAP sem Body");
        }
        if (body.getElementsByTagNameNS(ContratoRecepcaoEvento.NS_SOAP12, "Fault").getLength() > 0) {
            return new Invalida("SOAP Fault");
        }
        NodeList rets = body.getElementsByTagNameNS(ContratoEvento.NS_NFE, "retEnvEvento");
        if (rets.getLength() == 0) {
            return new Invalida("envelope sem retEnvEvento");
        }
        Element ret = (Element) rets.item(0);

        Optional<String> cStatLote = texto(ret, "cStat");
        if (cStatLote.isEmpty()) {
            return new Invalida("retEnvEvento sem cStat");
        }
        List<Element> retEventos = filhos(ret, ContratoEvento.NS_NFE, "retEvento");
        if (retEventos.isEmpty()) {
            return new Valida(cStatLote.get(), Optional.empty());
        }
        if (retEventos.size() > 1) {
            return new Invalida("retEnvEvento com mais de um retEvento");
        }
        Optional<Element> infEvento = filhos(retEventos.get(0), ContratoEvento.NS_NFE, "infEvento").stream().findFirst();
        Optional<String> cStatEvento = infEvento.flatMap(inf -> texto(inf, "cStat"));
        if (cStatEvento.isEmpty()) {
            return new Invalida("retEvento sem infEvento ou sem cStat");
        }
        Element inf = infEvento.get();
        return new Valida(cStatLote.get(), Optional.of(new RetornoEvento(cStatEvento.get(), texto(inf, "nProt"),
                texto(inf, "chNFe"), texto(inf, "tpEvento"), texto(inf, "nSeqEvento"))));
    }

    private static Optional<String> texto(Element pai, String nomeLocal) {
        return filhos(pai, ContratoEvento.NS_NFE, nomeLocal).stream().findFirst()
                .map(e -> e.getTextContent().trim())
                .filter(t -> !t.isEmpty());
    }

    private static List<Element> filhos(Element pai, String namespace, String nomeLocal) {
        List<Element> achados = new ArrayList<>();
        for (Node n = pai.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && namespace.equals(e.getNamespaceURI()) && nomeLocal.equals(e.getLocalName())) {
                achados.add(e);
            }
        }
        return achados;
    }

    private static boolean ehSoap12(Element e, String nomeLocal) {
        return ContratoRecepcaoEvento.NS_SOAP12.equals(e.getNamespaceURI()) && nomeLocal.equals(e.getLocalName());
    }
}
