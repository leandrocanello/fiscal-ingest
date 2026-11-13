package br.com.doc.ingest.adapter.nfe.cert;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.List;

/**
 * Truststore com as ACs raiz ICP-Brasil, montado em memória a partir de um diretório com
 * {@code indice.txt} ({@code <arquivo> <sha256>} por linha). Aceita certificados PEM e DER.
 * Mensagens de erro nomeiam o arquivo, nunca o conteúdo (inv. 13).
 */
public final class TruststoreIcpBrasil {

    static final String INDICE = "indice.txt";

    private TruststoreIcpBrasil() {
    }

    public static KeyStore deDiretorio(Path dir) {
        List<String[]> entradas = lerIndice(dir.resolve(INDICE));
        if (entradas.isEmpty()) {
            throw new IllegalStateException("indice.txt sem entradas");
        }
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            CertificateFactory fabrica = CertificateFactory.getInstance("X.509");
            for (String[] e : entradas) {
                byte[] bytes = lerArquivo(dir.resolve(e[0]), e[0]);
                if (!sha256(bytes).equalsIgnoreCase(e[1])) {
                    throw new IllegalStateException("sha256 divergente do indice.txt: " + e[0]);
                }
                X509Certificate cert = converter(fabrica, bytes, e[0]);
                ks.setCertificateEntry(e[0], cert);
            }
            return ks;
        } catch (GeneralSecurityException | IOException ex) {
            throw new IllegalStateException("falha ao montar o truststore", ex);
        }
    }

    private static List<String[]> lerIndice(Path indice) {
        try {
            return Files.readAllLines(indice).stream()
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

    private static byte[] lerArquivo(Path arquivo, String nome) {
        try {
            return Files.readAllBytes(arquivo);
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
