package br.com.doc.ingest.adapter.nfe.cert;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.DERBMPString;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.crypto.engines.DESedeEngine;
import org.bouncycastle.crypto.engines.RC2Engine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS12PfxPduBuilder;
import org.bouncycastle.pkcs.PKCS12SafeBag;
import org.bouncycastle.pkcs.PKCS12SafeBagBuilder;
import org.bouncycastle.pkcs.bc.BcPKCS12MacCalculatorBuilder;
import org.bouncycastle.pkcs.bc.BcPKCS12PBEOutputEncryptorBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS12SafeBagBuilder;

/**
 * Gera .pfx sintéticos em memória (par RSA 2048 + certificado autoassinado) para os testes do carregador.
 * Nunca grava em disco nem usa certificado real (inv. 13). Não registra o provider BouncyCastle globalmente.
 */
final class GeradorPfxDeTeste {

    static final String CN = "EMPRESA TESTE:00000000000191";
    private static final String ALIAS = "titular";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private GeradorPfxDeTeste() {
    }

    /** PKCS#12 no formato padrão do JDK 21 (PBES2/AES + HmacSHA256). */
    static byte[] moderno(char[] senha, Instant validoDe, Instant validoAte) {
        try {
            KeyPair par = novoPar();
            X509Certificate cert = autoassinado(par, validoDe, validoAte);
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            ks.setKeyEntry(ALIAS, par.getPrivate(), senha, new Certificate[] {cert});
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            ks.store(saida, senha);
            return saida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("falha ao gerar pfx moderno de teste", e);
        }
    }

    /** PKCS#12 legado: chave em PBEWithSHA1And3-KeyTripleDES-CBC, certificado em PBEWithSHA1And40BitRC2-CBC, MAC SHA-1. */
    static byte[] legado(char[] senha, Instant validoDe, Instant validoAte) {
        try {
            KeyPair par = novoPar();
            X509Certificate cert = autoassinado(par, validoDe, validoAte);
            DEROctetString idChave = new DEROctetString(
                    MessageDigest.getInstance("SHA-1").digest(par.getPublic().getEncoded()));
            DERBMPString nome = new DERBMPString(ALIAS);

            PKCS12SafeBagBuilder bagCert = new JcaPKCS12SafeBagBuilder(cert);
            bagCert.addBagAttribute(PKCS12SafeBag.friendlyNameAttribute, nome);
            bagCert.addBagAttribute(PKCS12SafeBag.localKeyIdAttribute, idChave);

            PKCS12SafeBagBuilder bagChave = new JcaPKCS12SafeBagBuilder(par.getPrivate(),
                    new BcPKCS12PBEOutputEncryptorBuilder(PKCSObjectIdentifiers.pbeWithSHAAnd3_KeyTripleDES_CBC,
                            CBCBlockCipher.newInstance(new DESedeEngine())).build(senha));
            bagChave.addBagAttribute(PKCS12SafeBag.friendlyNameAttribute, nome);
            bagChave.addBagAttribute(PKCS12SafeBag.localKeyIdAttribute, idChave);

            PKCS12PfxPduBuilder pfx = new PKCS12PfxPduBuilder();
            pfx.addEncryptedData(new BcPKCS12PBEOutputEncryptorBuilder(PKCSObjectIdentifiers.pbeWithSHAAnd40BitRC2_CBC,
                    CBCBlockCipher.newInstance(new RC2Engine())).build(senha), bagCert.build());
            pfx.addData(bagChave.build());
            return pfx.build(new BcPKCS12MacCalculatorBuilder(), senha).getEncoded(ASN1Encoding.DER);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao gerar pfx legado de teste", e);
        }
    }

    private static KeyPair novoPar() throws GeneralSecurityException {
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
        gerador.initialize(2048, ALEATORIO);
        return gerador.generateKeyPair();
    }

    private static X509Certificate autoassinado(KeyPair par, Instant validoDe, Instant validoAte) throws Exception {
        X500Name sujeito = new X500Name("CN=" + CN);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(sujeito,
                new BigInteger(64, ALEATORIO).abs().add(BigInteger.ONE),
                Date.from(validoDe), Date.from(validoAte), sujeito, par.getPublic());
        return new JcaX509CertificateConverter()
                .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(par.getPrivate())));
    }
}
