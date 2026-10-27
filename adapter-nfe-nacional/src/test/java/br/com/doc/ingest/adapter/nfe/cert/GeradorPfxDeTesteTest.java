package br.com.doc.ingest.adapter.nfe.cert;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.ContentInfo;
import org.bouncycastle.asn1.pkcs.EncryptedData;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.Pfx;
import org.bouncycastle.operator.bc.BcDefaultDigestProvider;
import org.bouncycastle.pkcs.PKCS12PfxPdu;
import org.bouncycastle.pkcs.PKCS12SafeBag;
import org.bouncycastle.pkcs.PKCS12SafeBagFactory;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.bc.BcPKCS12MacCalculatorBuilderProvider;
import org.junit.jupiter.api.Test;

/** O gerador de .pfx sintético (só em memória; nada commitado — inv. 13) produz o que os testes do carregador esperam. */
class GeradorPfxDeTesteTest {

    private static final char[] SENHA = "senha-sintetica".toCharArray();
    private static final Instant DE = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant ATE = Instant.parse("2027-01-01T00:00:00Z");

    @Test
    void geraPfxModernoQueOKeyStoreDoJdkAbre() throws Exception {
        byte[] pfx = GeradorPfxDeTeste.moderno(SENHA, DE, ATE);

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(pfx), SENHA);

        List<String> aliases = Collections.list(ks.aliases());
        assertThat(aliases).hasSize(1);
        String alias = aliases.get(0);
        assertThat(ks.isKeyEntry(alias)).isTrue();
        assertThat(ks.getKey(alias, SENHA)).isInstanceOf(PrivateKey.class);
        X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
        assertThat(cert.getSubjectX500Principal().getName()).contains("CN=EMPRESA TESTE:00000000000191");
        assertThat(cert.getNotBefore().toInstant()).isEqualTo(DE);
        assertThat(cert.getNotAfter().toInstant()).isEqualTo(ATE);
    }

    @Test
    void geraPfxLegadoCom3DesNaChaveRc2NoCertificadoEMacSha1() throws Exception {
        byte[] pfx = GeradorPfxDeTeste.legado(SENHA, DE, ATE);

        PKCS12PfxPdu pdu = new PKCS12PfxPdu(pfx);
        List<ASN1ObjectIdentifier> cifraCertificado = new ArrayList<>();
        List<ASN1ObjectIdentifier> cifraChave = new ArrayList<>();
        for (ContentInfo ci : pdu.getContentInfos()) {
            if (ci.getContentType().equals(PKCSObjectIdentifiers.encryptedData)) {
                cifraCertificado.add(EncryptedData.getInstance(ci.getContent())
                        .getEncryptionAlgorithm().getAlgorithm());
            } else {
                for (PKCS12SafeBag bag : new PKCS12SafeBagFactory(ci).getSafeBags()) {
                    if (bag.getBagValue() instanceof PKCS8EncryptedPrivateKeyInfo chave) {
                        cifraChave.add(chave.getEncryptionAlgorithm().getAlgorithm());
                    }
                }
            }
        }

        assertThat(cifraCertificado).containsExactly(PKCSObjectIdentifiers.pbeWithSHAAnd40BitRC2_CBC);
        assertThat(cifraChave).containsExactly(PKCSObjectIdentifiers.pbeWithSHAAnd3_KeyTripleDES_CBC);
        assertThat(Pfx.getInstance(pfx).getMacData().getMac().getAlgorithmId().getAlgorithm())
                .isEqualTo(OIWObjectIdentifiers.idSHA1);
        assertThat(pdu.isMacValid(new BcPKCS12MacCalculatorBuilderProvider(BcDefaultDigestProvider.INSTANCE), SENHA))
                .isTrue();
    }

    @Test
    void cadaChamadaGeraParDeChavesNovo() throws Exception {
        assertThat(GeradorPfxDeTeste.moderno(SENHA, DE, ATE)).isNotEqualTo(GeradorPfxDeTeste.moderno(SENHA, DE, ATE));
    }
}
