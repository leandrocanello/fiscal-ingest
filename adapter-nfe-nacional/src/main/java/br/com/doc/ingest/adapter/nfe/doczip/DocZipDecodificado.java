package br.com.doc.ingest.adapter.nfe.doczip;

import br.com.doc.ingest.domain.model.Nsu;

import java.util.Arrays;
import java.util.Objects;

/**
 * Resultado do decode de um {@code docZip}: o XML descompactado e a classificação do schema.
 *
 * @param nsu         NSU do atributo do docZip
 * @param tipo        schema classificado
 * @param schemaBruto valor original do atributo {@code schema}
 * @param xml         bytes do XML, exatamente como descompactados
 */
public record DocZipDecodificado(Nsu nsu, TipoSchemaDfe tipo, String schemaBruto, byte[] xml) {

    public DocZipDecodificado {
        Objects.requireNonNull(nsu, "nsu");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(xml, "xml");
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DocZipDecodificado d
                && nsu.equals(d.nsu) && tipo == d.tipo
                && Objects.equals(schemaBruto, d.schemaBruto) && Arrays.equals(xml, d.xml);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nsu, tipo, schemaBruto) * 31 + Arrays.hashCode(xml);
    }

    /** Nunca inclui o conteúdo do XML (invariante 13). */
    @Override
    public String toString() {
        return "DocZipDecodificado[nsu=" + nsu.valor() + ", tipo=" + tipo + ", bytes=" + xml.length + "]";
    }
}
