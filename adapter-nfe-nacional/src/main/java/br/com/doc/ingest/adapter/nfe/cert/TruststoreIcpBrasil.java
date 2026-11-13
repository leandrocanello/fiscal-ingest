package br.com.doc.ingest.adapter.nfe.cert;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Truststore com as ACs raiz ICP-Brasil, montado em memória a partir de um diretório com
 * {@code indice.txt} ({@code <arquivo> <sha256>} por linha). Aceita certificados PEM e DER.
 * Mensagens de erro nomeiam o arquivo, nunca o conteúdo (inv. 13).
 */
public final class TruststoreIcpBrasil {

    static final String INDICE = "indice.txt";

    private TruststoreIcpBrasil() {
    }

    public static final String VARIAVEL = "FISCAL_TRUSTSTORE";
    static final String PADRAO_CLASSPATH = "icp-brasil/";

    /** Sobrescrita por {@code FISCAL_TRUSTSTORE} (diretório com índice ou PKCS12 de senha vazia); senão o padrão do classpath. */
    public static KeyStore carregar(Map<String, String> env) {
        return carregar(env, TruststoreIcpBrasil.class.getClassLoader());
    }

    static KeyStore carregar(Map<String, String> env, ClassLoader loader) {
        String valor = env.get(VARIAVEL);
        if (valor == null || valor.isBlank()) {
            return montar(lerIndice(recurso(loader, INDICE)), nome -> recurso(loader, nome));
        }
        Path caminho = Path.of(valor);
        if (Files.isDirectory(caminho)) {
            return deDiretorio(caminho);
        }
        if (Files.isRegularFile(caminho)) {
            try (InputStream in = Files.newInputStream(caminho)) {
                KeyStore ks = KeyStore.getInstance("PKCS12");
                ks.load(in, new char[0]);
                return ks;
            } catch (GeneralSecurityException | IOException ex) {
                throw new IllegalStateException(VARIAVEL + " nao e um PKCS12 valido de senha vazia");
            }
        }
        throw new IllegalStateException(VARIAVEL + " aponta para caminho inexistente");
    }

    public static KeyStore deDiretorio(Path dir) {
        return montar(lerIndice(() -> abrir(dir.resolve(INDICE), INDICE)), nome -> () -> abrir(dir.resolve(nome), nome));
    }

    private interface Fonte {
        InputStream abrir();
    }

    private static Fonte recurso(ClassLoader loader, String nome) {
        return () -> {
            InputStream in = loader.getResourceAsStream(PADRAO_CLASSPATH + nome);
            if (in == null) {
                throw new IllegalStateException("arquivo do truststore ausente ou ilegivel: " + nome);
            }
            return in;
        };
    }

    private static InputStream abrir(Path arquivo, String nome) {
        try {
            return Files.newInputStream(arquivo);
        } catch (IOException e) {
            throw new IllegalStateException("arquivo do truststore ausente ou ilegivel: " + nome);
        }
    }

    private static KeyStore montar(List<String[]> entradas, Function<String, Fonte> fontes) {
        if (entradas.isEmpty()) {
            throw new IllegalStateException("indice.txt sem entradas");
        }
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            CertificateFactory fabrica = CertificateFactory.getInstance("X.509");
            for (String[] e : entradas) {
                byte[] bytes = lerBytes(fontes.apply(e[0]), e[0]);
                if (!sha256(bytes).equalsIgnoreCase(e[1])) {
                    throw new IllegalStateException("sha256 divergente do indice.txt: " + e[0]);
                }
                ks.setCertificateEntry(e[0], converter(fabrica, bytes, e[0]));
            }
            return ks;
        } catch (GeneralSecurityException | IOException ex) {
            throw new IllegalStateException("falha ao montar o truststore", ex);
        }
    }

    private static List<String[]> lerIndice(Fonte indice) {
        try (InputStream in = indice.abrir()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .map(l -> l.split("\\s+"))
                    .map(p -> {
                        if (p.length != 2) {
                            throw new IllegalStateException("linha invalida em indice.txt");
                        }
                        return p;
                    })
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("indice.txt ilegivel", e);
        }
    }

    private static byte[] lerBytes(Fonte fonte, String nome) {
        try (InputStream in = fonte.abrir()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("arquivo do truststore ausente ou ilegivel: " + nome);
        }
    }

    private static X509Certificate converter(CertificateFactory fabrica, byte[] bytes, String nome) {
        try {
            return (X509Certificate) fabrica.generateCertificate(new ByteArrayInputStream(bytes));
        } catch (GeneralSecurityException | ClassCastException e) {
            throw new IllegalStateException("arquivo nao e um certificado X.509: " + nome);
        }
    }

    private static String sha256(byte[] bytes) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
