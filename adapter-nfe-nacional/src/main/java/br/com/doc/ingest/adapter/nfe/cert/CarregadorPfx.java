package br.com.doc.ingest.adapter.nfe.cert;

import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.blob.Sha256;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;

/**
 * Abre um .pfx (PKCS#12) recebido em memória e devolve a chave privada do titular com a sua cadeia.
 *
 * <p>Nada é lido de disco nem registrado em log; a cópia da senha é zerada após o uso (inv. 13).
 */
public class CarregadorPfx {

    public CertificadoCarregado carregar(Credencial credencial) {
        byte[] pfx = credencial.pfx();
        char[] senha = credencial.senha();
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(pfx), senha);
            for (String alias : Collections.list(ks.aliases())) {
                if (ks.isKeyEntry(alias)) {
                    PrivateKey chave = (PrivateKey) ks.getKey(alias, senha);
                    X509Certificate[] cadeia = paraX509(ks.getCertificateChain(alias));
                    return new CertificadoCarregado(chave, cadeia, Sha256.hex(pfx),
                            cadeia[0].getNotBefore().toInstant(), cadeia[0].getNotAfter().toInstant());
                }
            }
            throw new IllegalStateException("pfx sem chave privada");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // a causa não é propagada para não arriscar vazar conteúdo do pfx (inv. 13)
            throw new IllegalStateException("não foi possível abrir o pfx");
        } finally {
            Arrays.fill(senha, '\0');
            Arrays.fill(pfx, (byte) 0);
        }
    }

    private static X509Certificate[] paraX509(Certificate[] cadeia) {
        return Arrays.stream(cadeia).map(X509Certificate.class::cast).toArray(X509Certificate[]::new);
    }
}
