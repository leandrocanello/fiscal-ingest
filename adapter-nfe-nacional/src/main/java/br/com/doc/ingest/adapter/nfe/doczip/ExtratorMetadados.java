package br.com.doc.ingest.adapter.nfe.doczip;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/** Lê o XML em streaming (StAX seguro) e extrai {@link MetadadosDfe}. Nunca ecoa conteúdo em erro. */
public final class ExtratorMetadados {

    private ExtratorMetadados() {
    }

    public static MetadadosDfe extrair(TipoSchemaDfe tipo, byte[] xml) {
        Coletor c = new Coletor();
        percorrer(xml, c::folha);
        return c.montar();
    }

    /** Percorre o XML chamando {@code folha} com o caminho (ex.: {@code resNFe/chNFe}) e o texto. */
    private static void percorrer(byte[] xml, java.util.function.BiConsumer<String, String> folha) {
        XMLStreamReader r = XmlSeguro.novoLeitor(xml);
        Deque<String> caminho = new ArrayDeque<>();
        StringBuilder texto = new StringBuilder();
        boolean temFilho = false;
        try {
            while (r.hasNext()) {
                switch (r.next()) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        caminho.addLast(r.getLocalName());
                        texto.setLength(0);
                        temFilho = false;
                        String id = r.getAttributeValue(null, "Id");
                        if (id != null) {
                            folha.accept(String.join("/", caminho) + "/@Id", id);
                        }
                    }
                    case XMLStreamConstants.CHARACTERS -> texto.append(r.getText());
                    case XMLStreamConstants.END_ELEMENT -> {
                        if (!temFilho) {
                            folha.accept(String.join("/", caminho), texto.toString().strip());
                        }
                        caminho.removeLast();
                        texto.setLength(0);
                        temFilho = true;
                    }
                    default -> { }
                }
            }
        } catch (XMLStreamException e) {
            throw new DocZipInvalidoException("XML malformado");
        } finally {
            try {
                r.close();
            } catch (XMLStreamException ignorado) {
                // nada a fazer
            }
        }
    }

    private static final class Coletor {
        String chave;
        String cnpjEmitente;
        String emissao;
        String valor;
        String situacao;
        String tipoOperacao;
        String documentoDestinatario;
        String cnpjTransportador;
        String serie;
        String numero;
        final List<String> autorizados = new ArrayList<>();

        void folha(String caminho, String texto) {
            int inf = caminho.indexOf("infNFe/");
            if (inf >= 0) {
                folhaInfNFe(caminho.substring(inf + "infNFe/".length()), texto);
                return;
            }
            switch (caminho) {
                case "resNFe/chNFe" -> chave = texto;
                case "resNFe/CNPJ" -> cnpjEmitente = texto;
                case "resNFe/dhEmi" -> emissao = texto;
                case "resNFe/vNF" -> valor = texto;
                case "resNFe/cSitNFe" -> situacao = texto;
                case "resNFe/tpNF" -> tipoOperacao = texto;
                default -> { }
            }
        }

        private void folhaInfNFe(String caminho, String texto) {
            switch (caminho) {
                case "@Id" -> chave = texto.startsWith("NFe") ? texto.substring(3) : texto;
                case "ide/serie" -> serie = texto;
                case "ide/nNF" -> numero = texto;
                case "ide/dhEmi" -> emissao = texto;
                case "emit/CNPJ" -> cnpjEmitente = texto;
                case "dest/CNPJ", "dest/CPF" -> documentoDestinatario = texto;
                case "autXML/CNPJ" -> autorizados.add(texto);
                case "transp/transporta/CNPJ" -> cnpjTransportador = texto;
                case "total/ICMSTot/vNF" -> valor = texto;
                default -> { }
            }
        }

        MetadadosDfe montar() {
            if (chave == null || !chave.matches("\\d{44}")) {
                throw new DocZipInvalidoException("Chave de acesso ausente ou inválida");
            }
            return new MetadadosDfe(chave, chave.substring(20, 22),
                    Optional.ofNullable(cnpjEmitente), Optional.ofNullable(documentoDestinatario), autorizados,
                    Optional.ofNullable(cnpjTransportador),
                    Optional.ofNullable(serie).map(v -> inteiro(v, "Série inválida")),
                    Optional.ofNullable(numero).map(v -> longo(v, "Número inválido")),
                    Optional.ofNullable(emissao).map(ExtratorMetadados::data),
                    Optional.ofNullable(valor).map(ExtratorMetadados::decimal),
                    Optional.empty(), Optional.empty(),
                    Optional.ofNullable(situacao), Optional.ofNullable(tipoOperacao));
        }
    }

    private static OffsetDateTime data(String s) {
        try {
            return OffsetDateTime.parse(s);
        } catch (DateTimeParseException e) {
            throw new DocZipInvalidoException("Data de emissão inválida");
        }
    }

    private static Integer inteiro(String s, String erro) {
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            throw new DocZipInvalidoException(erro);
        }
    }

    private static Long longo(String s, String erro) {
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            throw new DocZipInvalidoException(erro);
        }
    }

    private static BigDecimal decimal(String s) {
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new DocZipInvalidoException("Valor total inválido");
        }
    }
}
