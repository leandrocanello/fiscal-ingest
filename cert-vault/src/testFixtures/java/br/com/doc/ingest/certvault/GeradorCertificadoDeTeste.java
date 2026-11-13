package br.com.doc.ingest.certvault;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERBMPString;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
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

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Gera cadeias e .pfx sintéticos em memória (raiz → intermediária → titular), adaptado de
 * {@code GeradorPfxDeTeste} do adapter (DA-35). Nunca grava em disco nem usa certificado real (inv. 13).
 * Não registra o provider BouncyCastle globalmente e não expõe tipos dele.
 */
public final class GeradorCertificadoDeTeste {

    /** Como o valor do CNPJ é codificado dentro do {@code otherName}. */
    public enum CodificacaoCnpj { OCTET_STRING, PRINTABLE_STRING, UTF8_STRING }

    /**
     * Certificado final com a sua chave.
     *
     * @param cadeia certificados emissores, do mais próximo ao mais distante, sem o próprio titular
     */
    public record CertificadoTitular(KeyPair par, X509Certificate certificado, List<X509Certificate> cadeia) {
    }

    private record Ac(KeyPair par, X509Certificate certificado, X500Name nome) {
    }

    private static final ASN1ObjectIdentifier OID_RESPONSAVEL = new ASN1ObjectIdentifier("2.16.76.1.3.2");
    private static final ASN1ObjectIdentifier OID_CNPJ = new ASN1ObjectIdentifier("2.16.76.1.3.3");
    private static final ASN1ObjectIdentifier OID_CPF = new ASN1ObjectIdentifier("2.16.76.1.3.1");
    private static final X500Name NOME_RAIZ = new X500Name("CN=AC RAIZ SINTETICA,O=TESTE");
    private static final X500Name NOME_INTERMEDIARIA = new X500Name("CN=AC INTERMEDIARIA SINTETICA,O=TESTE");
    private static final String ALIAS = "titular";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final Instant validoDe;
    private final Instant validoAte;
    private final Ac raiz;
    private final Ac intermediaria;

    /** A raiz, a intermediária e, por padrão, os titulares valem de {@code validoDe} a {@code validoAte}. */
    public GeradorCertificadoDeTeste(Instant validoDe, Instant validoAte) {
        this.validoDe = validoDe;
        this.validoAte = validoAte;
        this.raiz = novaRaiz();
        this.intermediaria = novaIntermediaria(raiz);
    }

    public X509Certificate raiz() {
        return raiz.certificado();
    }

    public X509Certificate intermediaria() {
        return intermediaria.certificado();
    }

    public CertificadoTitular titular(String cnpj) {
        return titular(cnpj, CodificacaoCnpj.OCTET_STRING);
    }

    public CertificadoTitular titular(String cnpj, CodificacaoCnpj codificacao) {
        return emitido(cnpj, sanDoCnpj(cnpj, codificacao), intermediaria, validoDe, validoAte,
                List.of(intermediaria.certificado(), raiz.certificado()));
    }

    public CertificadoTitular titularComValidade(String cnpj, Instant de, Instant ate) {
        return emitido(cnpj, sanDoCnpj(cnpj, CodificacaoCnpj.OCTET_STRING), intermediaria, de, ate,
                List.of(intermediaria.certificado(), raiz.certificado()));
    }

    /** Sem a extensão SAN: o CNPJ aparece só no CN. */
    public CertificadoTitular semSan(String cnpj) {
        return emitido(cnpj, null, intermediaria, validoDe, validoAte,
                List.of(intermediaria.certificado(), raiz.certificado()));
    }

    /** e-CPF: {@code otherName} {@code 2.16.76.1.3.1}, sem o OID de CNPJ. */
    public CertificadoTitular eCpf(String cpf) {
        GeneralNames san = new GeneralNames(outroNome(OID_CPF,
                new DEROctetString(("01011980" + cpf).getBytes(StandardCharsets.US_ASCII))));
        return emitido(cpf, san, intermediaria, validoDe, validoAte,
                List.of(intermediaria.certificado(), raiz.certificado()));
    }

    /** Traz o CNPJ correto, mas foi assinado pela própria chave, fora de qualquer AC. */
    public CertificadoTitular autoassinado(String cnpj) {
        try {
            KeyPair par = novoPar();
            X500Name nome = nomeDoTitular(cnpj);
            JcaX509v3CertificateBuilder builder = construtor(nome, nome, par, validoDe, validoAte);
            extensoesDeTitular(builder, sanDoCnpj(cnpj, CodificacaoCnpj.OCTET_STRING));
            return new CertificadoTitular(par, assinar(builder, par), List.of());
        } catch (Exception e) {
            throw new IllegalStateException("falha ao gerar certificado autoassinado de teste", e);
        }
    }

    /** Assinado por um certificado final (sem {@code cA}), que por sua vez descende da cadeia verdadeira. */
    public CertificadoTitular emitidoPorCertificadoFinal(String cnpj) {
        CertificadoTitular emissor = emitido("EMISSOR FINAL", null, intermediaria, validoDe, validoAte,
                List.of(intermediaria.certificado(), raiz.certificado()));
        Ac falsaAc = new Ac(emissor.par(), emissor.certificado(), nomeDoTitular("EMISSOR FINAL"));
        return emitido(cnpj, sanDoCnpj(cnpj, CodificacaoCnpj.OCTET_STRING), falsaAc, validoDe, validoAte,
                List.of(emissor.certificado(), intermediaria.certificado(), raiz.certificado()));
    }

    /** Emitido por raiz e intermediária com os mesmos nomes das verdadeiras, mas com outras chaves. */
    public CertificadoTitular deAcFalsaDeMesmoNome(String cnpj) {
        Ac raizFalsa = novaRaiz();
        Ac intermediariaFalsa = novaIntermediaria(raizFalsa);
        return emitido(cnpj, sanDoCnpj(cnpj, CodificacaoCnpj.OCTET_STRING), intermediariaFalsa, validoDe, validoAte,
                List.of(intermediariaFalsa.certificado(), raizFalsa.certificado()));
    }

    /** PKCS#12 no formato padrão do JDK 21 (PBES2/AES + HmacSHA256), com a cadeia completa. */
    public byte[] pfxModerno(CertificadoTitular titular, char[] senha) {
        List<Certificate> cadeia = new ArrayList<>();
        cadeia.add(titular.certificado());
        cadeia.addAll(titular.cadeia());
        return pfxComChave(titular, cadeia, senha);
    }

    /** PKCS#12 só com o certificado final: as intermediárias precisam vir do acervo. */
    public byte[] pfxSemIntermediarias(CertificadoTitular titular, char[] senha) {
        return pfxComChave(titular, List.of(titular.certificado()), senha);
    }

    /** PKCS#12 só com um certificado confiável, sem nenhuma chave privada. */
    public byte[] pfxSemChave(CertificadoTitular titular, char[] senha) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            ks.setCertificateEntry(ALIAS, titular.certificado());
            return gravar(ks, senha);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao gerar pfx sem chave de teste", e);
        }
    }

    /** PKCS#12 com duas chaves privadas (titular ambíguo). */
    public byte[] pfxComDuasChaves(String cnpj, char[] senha) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            for (String alias : new String[] {ALIAS, ALIAS + "-2"}) {
                CertificadoTitular titular = titular(cnpj);
                ks.setKeyEntry(alias, titular.par().getPrivate(), senha, new Certificate[] {titular.certificado()});
            }
            return gravar(ks, senha);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao gerar pfx com duas chaves de teste", e);
        }
    }

    /** PKCS#12 legado: chave em PBEWithSHA1And3-KeyTripleDES-CBC, certificados em PBEWithSHA1And40BitRC2-CBC, MAC SHA-1. */
    public byte[] pfxLegado(CertificadoTitular titular, char[] senha) {
        try {
            DEROctetString idChave = new DEROctetString(
                    MessageDigest.getInstance("SHA-1").digest(titular.par().getPublic().getEncoded()));
            DERBMPString nome = new DERBMPString(ALIAS);

            PKCS12SafeBagBuilder bagCert = new JcaPKCS12SafeBagBuilder(titular.certificado());
            bagCert.addBagAttribute(PKCS12SafeBag.friendlyNameAttribute, nome);
            bagCert.addBagAttribute(PKCS12SafeBag.localKeyIdAttribute, idChave);
            List<PKCS12SafeBag> bagsCert = new ArrayList<>();
            bagsCert.add(bagCert.build());
            for (X509Certificate emissor : titular.cadeia()) {
                bagsCert.add(new JcaPKCS12SafeBagBuilder(emissor).build());
            }

            PKCS12SafeBagBuilder bagChave = new JcaPKCS12SafeBagBuilder(titular.par().getPrivate(),
                    new BcPKCS12PBEOutputEncryptorBuilder(PKCSObjectIdentifiers.pbeWithSHAAnd3_KeyTripleDES_CBC,
                            CBCBlockCipher.newInstance(new DESedeEngine())).build(senha));
            bagChave.addBagAttribute(PKCS12SafeBag.friendlyNameAttribute, nome);
            bagChave.addBagAttribute(PKCS12SafeBag.localKeyIdAttribute, idChave);

            PKCS12PfxPduBuilder pfx = new PKCS12PfxPduBuilder();
            pfx.addEncryptedData(new BcPKCS12PBEOutputEncryptorBuilder(PKCSObjectIdentifiers.pbeWithSHAAnd40BitRC2_CBC,
                    CBCBlockCipher.newInstance(new RC2Engine())).build(senha), bagsCert.toArray(new PKCS12SafeBag[0]));
            pfx.addData(bagChave.build());
            return pfx.build(new BcPKCS12MacCalculatorBuilder(), senha).getEncoded(ASN1Encoding.DER);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao gerar pfx legado de teste", e);
        }
    }

    private byte[] pfxComChave(CertificadoTitular titular, List<Certificate> cadeia, char[] senha) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            ks.setKeyEntry(ALIAS, titular.par().getPrivate(), senha, cadeia.toArray(new Certificate[0]));
            return gravar(ks, senha);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao gerar pfx de teste", e);
        }
    }

    private static byte[] gravar(KeyStore ks, char[] senha) throws Exception {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ks.store(saida, senha);
        return saida.toByteArray();
    }

    private Ac novaRaiz() {
        try {
            KeyPair par = novoPar();
            JcaX509v3CertificateBuilder builder = construtor(NOME_RAIZ, NOME_RAIZ, par, validoDe, validoAte);
            extensoesDeAc(builder, new BasicConstraints(true));
            return new Ac(par, assinar(builder, par), NOME_RAIZ);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao gerar raiz de teste", e);
        }
    }

    private Ac novaIntermediaria(Ac emissora) {
        try {
            KeyPair par = novoPar();
            JcaX509v3CertificateBuilder builder =
                    construtor(emissora.nome(), NOME_INTERMEDIARIA, par, validoDe, validoAte);
            extensoesDeAc(builder, new BasicConstraints(0));
            return new Ac(par, assinar(builder, emissora.par()), NOME_INTERMEDIARIA);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao gerar intermediária de teste", e);
        }
    }

    private static CertificadoTitular emitido(String identificador, GeneralNames san, Ac emissora, Instant de,
                                              Instant ate, List<X509Certificate> cadeia) {
        try {
            KeyPair par = novoPar();
            JcaX509v3CertificateBuilder builder =
                    construtor(emissora.nome(), nomeDoTitular(identificador), par, de, ate);
            extensoesDeTitular(builder, san);
            return new CertificadoTitular(par, assinar(builder, emissora.par()), cadeia);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao gerar titular de teste", e);
        }
    }

    private static X500Name nomeDoTitular(String identificador) {
        return new X500Name("CN=EMPRESA TESTE:" + identificador + ",O=TESTE");
    }

    private static GeneralNames sanDoCnpj(String cnpj, CodificacaoCnpj codificacao) {
        ASN1Encodable valor = switch (codificacao) {
            case OCTET_STRING -> new DEROctetString(cnpj.getBytes(StandardCharsets.US_ASCII));
            case PRINTABLE_STRING -> new DERPrintableString(cnpj);
            case UTF8_STRING -> new DERUTF8String(cnpj);
        };
        return new GeneralNames(new GeneralName[] {
                outroNome(OID_RESPONSAVEL, new DEROctetString("RESPONSAVEL TESTE".getBytes(StandardCharsets.US_ASCII))),
                outroNome(OID_CNPJ, valor)});
    }

    private static GeneralName outroNome(ASN1ObjectIdentifier oid, ASN1Encodable valor) {
        return new GeneralName(GeneralName.otherName,
                new DERSequence(new ASN1Encodable[] {oid, new DERTaggedObject(true, 0, valor)}));
    }

    private static JcaX509v3CertificateBuilder construtor(X500Name emissor, X500Name sujeito, KeyPair par,
                                                          Instant de, Instant ate) {
        return new JcaX509v3CertificateBuilder(emissor, new BigInteger(64, ALEATORIO).add(BigInteger.ONE),
                Date.from(de), Date.from(ate), sujeito, par.getPublic());
    }

    private static void extensoesDeAc(JcaX509v3CertificateBuilder builder, BasicConstraints restricoes)
            throws Exception {
        builder.addExtension(Extension.basicConstraints, true, restricoes);
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
    }

    private static void extensoesDeTitular(JcaX509v3CertificateBuilder builder, GeneralNames san) throws Exception {
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        if (san != null) {
            builder.addExtension(Extension.subjectAlternativeName, false, san);
        }
    }

    private static X509Certificate assinar(JcaX509v3CertificateBuilder builder, KeyPair assinante) throws Exception {
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(assinante.getPrivate())));
    }

    private static KeyPair novoPar() throws Exception {
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
        gerador.initialize(2048, ALEATORIO);
        return gerador.generateKeyPair();
    }
}
