package br.com.doc.ingest.certvault;

import br.com.doc.ingest.certvault.ResultadoValidacao.PfxValidado;
import br.com.doc.ingest.certvault.ResultadoValidacao.Recusado;
import br.com.doc.ingest.domain.blob.Sha256;
import br.com.doc.ingest.domain.cofre.MotivoRecusa;
import br.com.doc.ingest.domain.model.Cnpj;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Valida o .pfx recebido no upload antes de ele ser cifrado e guardado (CLAUDE.md §7.4).
 *
 * <p>A abertura repete a do {@code CarregadorPfx} do adapter com um {@code KeyStore} próprio, para manter a
 * regra de dependência da §19 (DA-35). O {@code KeyStore} PKCS12 do JDK 21 abre o formato moderno e o legado.
 *
 * <p>O .pfx vem de fora, então é entrada não confiável: o tamanho é conferido antes de qualquer parse, toda
 * falha vira {@link Recusado} com {@link MotivoRecusa}, sem exceção, sem causa e sem senha ou bytes (inv. 13).
 * Os arrays do chamador não são alterados nem zerados: o cofre ainda precisa deles para cifrar.
 */
public final class ValidadorPfx {

    /** Um A1 real tem poucos KiB; 64 KiB dá folga para cadeias longas sem aceitar arquivo arbitrário. */
    public static final int LIMITE_PADRAO_BYTES = 64 * 1024;

    private final ConfiancaIcp confianca;
    private final Clock relogio;
    private final int limiteBytes;

    public ValidadorPfx(ConfiancaIcp confianca, Clock relogio, int limiteBytes) {
        this.confianca = Objects.requireNonNull(confianca, "confianca");
        this.relogio = Objects.requireNonNull(relogio, "relogio");
        if (limiteBytes <= 0) {
            throw new IllegalArgumentException("limiteBytes deve ser positivo");
        }
        this.limiteBytes = limiteBytes;
    }

    public ResultadoValidacao validar(byte[] pfx, char[] senha, Cnpj cnpjTitular) {
        Objects.requireNonNull(pfx, "pfx");
        Objects.requireNonNull(senha, "senha");
        Objects.requireNonNull(cnpjTitular, "cnpjTitular");
        if (pfx.length > limiteBytes) {
            return new Recusado(MotivoRecusa.TAMANHO_EXCEDIDO);
        }
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(pfx), senha);
            List<String> aliases = aliasesDeChave(ks);
            if (aliases.isEmpty()) {
                return new Recusado(MotivoRecusa.SEM_CHAVE_PRIVADA);
            }
            if (aliases.size() > 1) {
                return new Recusado(MotivoRecusa.AMBIGUO);
            }
            String alias = aliases.get(0);
            // confere que a chave abre com a senha; a referência é descartada em seguida
            ks.getKey(alias, senha);
            Certificate[] cadeia = ks.getCertificateChain(alias);
            if (cadeia == null || cadeia.length == 0 || !(cadeia[0] instanceof X509Certificate titular)) {
                return new Recusado(MotivoRecusa.ILEGIVEL);
            }
            return new PfxValidado(Sha256.hex(pfx),
                    titular.getNotBefore().toInstant(), titular.getNotAfter().toInstant());
        } catch (IOException e) {
            // o JDK sinaliza MAC/senha incorreta com IOException causada por UnrecoverableKeyException
            return new Recusado(e.getCause() instanceof UnrecoverableKeyException
                    ? MotivoRecusa.SENHA_INCORRETA : MotivoRecusa.ILEGIVEL);
        } catch (UnrecoverableKeyException e) {
            return new Recusado(MotivoRecusa.SENHA_INCORRETA);
        } catch (Exception e) {
            // a causa não é propagada para não arriscar vazar conteúdo do pfx (inv. 13)
            return new Recusado(MotivoRecusa.ILEGIVEL);
        }
    }

    private static List<String> aliasesDeChave(KeyStore ks) throws KeyStoreException {
        List<String> aliases = new ArrayList<>();
        for (String alias : Collections.list(ks.aliases())) {
            if (ks.isKeyEntry(alias)) {
                aliases.add(alias);
            }
        }
        return aliases;
    }
}
