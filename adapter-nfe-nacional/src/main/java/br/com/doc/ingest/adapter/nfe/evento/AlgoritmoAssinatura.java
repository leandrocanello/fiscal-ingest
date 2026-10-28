package br.com.doc.ingest.adapter.nfe.evento;

import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.SignatureMethod;

/**
 * Par {@code SignatureMethod}/{@code DigestMethod} da assinatura XML-DSig.
 *
 * <p>Padrão {@link #RSA_SHA1}: MOC 7.0 Tab. 4-2, p. 54–55 (decisão E2-S11 D1, 2026-10-27).
 * [VALIDAR docs/FONTES-OFICIAIS.md item 7]: conferir no smoke de homologação.
 */
public enum AlgoritmoAssinatura {
    RSA_SHA1(SignatureMethod.RSA_SHA1, DigestMethod.SHA1),
    RSA_SHA256(SignatureMethod.RSA_SHA256, DigestMethod.SHA256);

    public static final AlgoritmoAssinatura PADRAO = RSA_SHA1;

    private final String signatureMethod;
    private final String digestMethod;

    AlgoritmoAssinatura(String signatureMethod, String digestMethod) {
        this.signatureMethod = signatureMethod;
        this.digestMethod = digestMethod;
    }

    public String signatureMethod() {
        return signatureMethod;
    }

    public String digestMethod() {
        return digestMethod;
    }
}
