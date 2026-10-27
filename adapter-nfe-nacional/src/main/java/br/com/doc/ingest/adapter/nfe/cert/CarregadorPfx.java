package br.com.doc.ingest.adapter.nfe.cert;

import br.com.doc.ingest.domain.adapter.Credencial;
import br.com.doc.ingest.domain.blob.Sha256;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Abre um .pfx (PKCS#12) recebido em memória e devolve a chave privada do titular com a sua cadeia.
 *
 * <p>Exige exatamente uma chave privada no .pfx. Falhas viram {@link CertificadoInvalidoException} com
 * {@link MotivoCertificadoInvalido}, sem senha nem bytes na mensagem.
 *
 * <p>Nada é lido de disco nem registrado em log; a cópia da senha é zerada após o uso (inv. 13).
 */
public class CarregadorPfx {

    public CertificadoCarregado carregar(Credencial credencial) {
        byte[] pfx = credencial.pfx();
        char[] senha = credencial.senha();
        try {
            KeyStore ks = abrir(pfx, senha);
            String alias = unicoAliasDeChave(ks);
            PrivateKey chave = (PrivateKey) ks.getKey(alias, senha);
            X509Certificate[] cadeia = paraX509(ks.getCertificateChain(alias));
            return new CertificadoCarregado(chave, cadeia, Sha256.hex(pfx),
                    cadeia[0].getNotBefore().toInstant(), cadeia[0].getNotAfter().toInstant());
        } catch (CertificadoInvalidoException e) {
            throw e;
        } catch (UnrecoverableKeyException e) {
            throw new CertificadoInvalidoException(MotivoCertificadoInvalido.SENHA_INCORRETA);
        } catch (Exception e) {
            // a causa não é propagada para não arriscar vazar conteúdo do pfx (inv. 13)
            throw new CertificadoInvalidoException(MotivoCertificadoInvalido.ILEGIVEL);
        } finally {
            Arrays.fill(senha, '\0');
            Arrays.fill(pfx, (byte) 0);
        }
    }

    private static KeyStore abrir(byte[] pfx, char[] senha) throws GeneralSecurityException {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try {
            ks.load(new ByteArrayInputStream(pfx), senha);
        } catch (IOException e) {
            // o JDK sinaliza MAC/senha incorreta com IOException causada por UnrecoverableKeyException
            throw e.getCause() instanceof UnrecoverableKeyException
                    ? new CertificadoInvalidoException(MotivoCertificadoInvalido.SENHA_INCORRETA)
                    : new CertificadoInvalidoException(MotivoCertificadoInvalido.ILEGIVEL);
        }
        return ks;
    }

    private static String unicoAliasDeChave(KeyStore ks) throws KeyStoreException {
        List<String> aliases = new ArrayList<>();
        for (String alias : Collections.list(ks.aliases())) {
            if (ks.isKeyEntry(alias)) {
                aliases.add(alias);
            }
        }
        if (aliases.isEmpty()) {
            throw new CertificadoInvalidoException(MotivoCertificadoInvalido.SEM_CHAVE_PRIVADA);
        }
        if (aliases.size() > 1) {
            throw new CertificadoInvalidoException(MotivoCertificadoInvalido.AMBIGUO);
        }
        return aliases.get(0);
    }

    private static X509Certificate[] paraX509(Certificate[] cadeia) {
        return Arrays.stream(cadeia).map(X509Certificate.class::cast).toArray(X509Certificate[]::new);
    }
}
