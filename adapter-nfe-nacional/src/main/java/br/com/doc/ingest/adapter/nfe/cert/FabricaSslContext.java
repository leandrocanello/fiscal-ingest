package br.com.doc.ingest.adapter.nfe.cert;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Monta o {@link SSLContext} mTLS com o certificado do titular do CNPJ consultado.
 *
 * <p>A chave fica num {@code KeyStore} só em memória, protegido por uma senha efêmera aleatória zerada após o
 * uso (inv. 13). O {@code KeyStore} de confiança é obrigatório e injetado: o AN usa ACs ICP-Brasil, que não
 * estão no {@code cacerts} do JDK. Contexto {@code TLSv1.3}, que negocia TLS 1.3 ou 1.2 (1.0/1.1 ficam
 * desabilitados pela política do JDK 21).
 */
public class FabricaSslContext {

    private static final String PROTOCOLO = "TLSv1.3";
    private static final String ALIAS = "titular";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    public SSLContext criar(CertificadoCarregado certificado, KeyStore confianca) {
        Objects.requireNonNull(certificado, "certificado");
        Objects.requireNonNull(confianca, "confianca");
        char[] senhaEfemera = senhaEfemera();
        try {
            KeyStore chaves = KeyStore.getInstance("PKCS12");
            chaves.load(null, null);
            chaves.setKeyEntry(ALIAS, certificado.chavePrivada(), senhaEfemera, certificado.cadeia());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(chaves, senhaEfemera);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(confianca);

            SSLContext contexto = SSLContext.getInstance(PROTOCOLO);
            contexto.init(kmf.getKeyManagers(), tmf.getTrustManagers(), ALEATORIO);
            return contexto;
        } catch (GeneralSecurityException | IOException e) {
            // a causa não é propagada para não arriscar vazar material da chave (inv. 13)
            throw new IllegalStateException("falha ao montar SSLContext mTLS: " + e.getClass().getSimpleName());
        } finally {
            Arrays.fill(senhaEfemera, '\0');
        }
    }

    private static char[] senhaEfemera() {
        char[] senha = new char[32];
        for (int i = 0; i < senha.length; i++) {
            senha[i] = (char) ('!' + ALEATORIO.nextInt(94));
        }
        return senha;
    }
}
