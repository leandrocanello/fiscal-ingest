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

    /** Extrai os metadados; o elemento raiz precisa corresponder ao tipo. {@code DESCONHECIDO} é rejeitado. */
    public static MetadadosDfe extrair(TipoSchemaDfe tipo, byte[] xml) {
        String raizEsperada = raizEsperada(tipo);
        Coletor c = new Coletor();
        percorrer(xml, raiz -> {
            if (!raiz.equals(raizEsperada)) {
                throw new DocZipInvalidoException("Elemento raiz diverge do schema informado");
            }
        }, c::folha);
        return c.montar();
    }

    /** Como {@link #extrair}, mas schema desconhecido não é erro: devolve vazio (o bruto é preservado pelo chamador). */
    public static Optional<MetadadosDfe> extrairSeConhecido(TipoSchemaDfe tipo, byte[] xml) {
        if (tipo == TipoSchemaDfe.DESCONHECIDO) {
            return Optional.empty();
        }
        return Optional.of(extrair(tipo, xml));
    }

    private static String raizEsperada(TipoSchemaDfe tipo) {
        return switch (tipo) {
            case RES_NFE -> "resNFe";
            case PROC_NFE -> "nfeProc";
            case RES_EVENTO -> "resEvento";
            case PROC_EVENTO_NFE -> "procEventoNFe";
            case DESCONHECIDO -> throw new DocZipInvalidoException("Schema desconhecido não tem metadados");
        };
    }

    /** Percorre o XML chamando {@code folha} com o caminho (ex.: {@code resNFe/chNFe}) e o texto. */
    private static void percorrer(byte[] xml, java.util.function.Consumer<String> raiz,
                                  java.util.function.BiConsumer<String, String> folha) {
        XMLStreamReader r = XmlSeguro.novoLeitor(xml);
        Deque<String> caminho = new ArrayDeque<>();
        StringBuilder texto = new StringBuilder();
        boolean temFilho = false;
        try {
            while (r.hasNext()) {
                switch (r.next()) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        caminho.addLast(r.getLocalName());
                        if (caminho.size() == 1) {
                            raiz.accept(r.getLocalName());
                        }
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
        String tipoEvento;
        String seqEvento;
        String autorEvento;
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
                case "resEvento/chNFe", "procEventoNFe/evento/infEvento/chNFe" -> chave = texto;
                case "resEvento/CNPJ", "procEventoNFe/evento/infEvento/CNPJ" -> autorEvento = texto;
                case "resEvento/tpEvento", "procEventoNFe/evento/infEvento/tpEvento" -> tipoEvento = texto;
                case "resEvento/nSeqEvento", "procEventoNFe/evento/infEvento/nSeqEvento" -> seqEvento = texto;
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
                    Optional.ofNullable(tipoEvento),
                    Optional.ofNullable(seqEvento).map(v -> inteiro(v, "Sequência do evento inválida")),
                    Optional.ofNullable(situacao), Optional.ofNullable(tipoOperacao),
                    Optional.ofNullable(autorEvento));
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
