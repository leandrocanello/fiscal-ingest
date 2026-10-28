package br.com.doc.ingest.adapter.nfe.evento;

import br.com.doc.ingest.adapter.nfe.cert.CertificadoCarregado;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import java.util.List;
import java.util.Objects;

/**
 * Assina o {@code infEvento} com XML-DSig enveloped (API {@code javax.xml.crypto.dsig} do JDK): {@code Signature}
 * filha de {@code evento}, logo após o {@code infEvento}; {@code Reference URI="#Id"}; transforms
 * enveloped-signature + C14N inclusivo; {@code KeyInfo/X509Data} só com o certificado do titular.
 *
 * <p>Fonte: MOC 7.0 Tab. 4-2, p. 54–55 (decisão E2-S11 D1/D2). [VALIDAR docs/FONTES-OFICIAIS.md item 7].
 */
public final class AssinadorXml {

    private final AlgoritmoAssinatura algoritmo;

    public AssinadorXml(AlgoritmoAssinatura algoritmo) {
        this.algoritmo = Objects.requireNonNull(algoritmo, "algoritmo");
    }

    public void assinar(Document doc, String id, CertificadoCarregado certificado) {
        Objects.requireNonNull(doc, "doc");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(certificado, "certificado");
        Element infEvento = infEventoComId(doc, id);
        infEvento.setIdAttributeNS(null, "Id", true);
        try {
            XMLSignatureFactory fabrica = XMLSignatureFactory.getInstance("DOM");
            Reference referencia = fabrica.newReference("#" + id,
                    fabrica.newDigestMethod(algoritmo.digestMethod(), null),
                    List.of(fabrica.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                            fabrica.newTransform(CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null)),
                    null, null);
            SignedInfo signedInfo = fabrica.newSignedInfo(
                    fabrica.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                    fabrica.newSignatureMethod(algoritmo.signatureMethod(), null),
                    List.of(referencia));
            KeyInfoFactory kif = fabrica.getKeyInfoFactory();
            KeyInfo keyInfo = kif.newKeyInfo(List.of(kif.newX509Data(List.of(certificado.cadeia()[0]))));
            DOMSignContext contexto = new DOMSignContext(certificado.chavePrivada(), infEvento.getParentNode());
            fabrica.newXMLSignature(signedInfo, keyInfo).sign(contexto);
        } catch (Exception e) {
            // a causa não é propagada para não arriscar vazar material de chave (inv. 13)
            throw new AssinaturaInvalidaException("falha ao assinar o evento (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static Element infEventoComId(Document doc, String id) {
        NodeList lista = doc.getElementsByTagNameNS(ContratoEvento.NS_NFE, "infEvento");
        for (int i = 0; i < lista.getLength(); i++) {
            Element e = (Element) lista.item(i);
            if (id.equals(e.getAttribute("Id"))) {
                return e;
            }
        }
        throw new AssinaturaInvalidaException("infEvento com o Id informado não encontrado");
    }
}
