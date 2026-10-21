/**
 * Modelos JAXB do serviço NFeDistribuicaoDFe.
 *
 * <p>Os elementos filhos ({@code tpAmb}, {@code CNPJ}, {@code cStat}, {@code docZip}...) herdam o
 * namespace padrão da NF-e no XML. Sem {@code elementFormDefault = QUALIFIED}, o JAXB espera esses
 * elementos sem namespace e os campos ficam nulos no unmarshal.
 *
 * <p>Arquivo recriado na Fase 1.5: não foi recuperado do histórico (os testes de
 * {@code DistDfeJaxbTest} dependem dele).
 */
@XmlSchema(
        namespace = "http://www.portalfiscal.inf.br/nfe",
        elementFormDefault = XmlNsForm.QUALIFIED)
package br.com.doc.ingest.adapter.nfe.soap;

import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;
