package br.com.doc.ingest.certvault;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Acervo de ACs confiáveis para validar a cadeia do certificado no upload (DA-36). As raízes são as âncoras;
 * as intermediárias completam um .pfx que não as traz. Quem carrega as ACs ICP-Brasil reais são os {@code app-*}.
 */
public record ConfiancaIcp(Set<X509Certificate> raizes, Collection<X509Certificate> intermediarias) {

    public ConfiancaIcp {
        Objects.requireNonNull(raizes, "raizes");
        Objects.requireNonNull(intermediarias, "intermediarias");
        if (raizes.isEmpty()) {
            throw new IllegalArgumentException("o acervo precisa de ao menos uma raiz");
        }
        raizes = Set.copyOf(raizes);
        intermediarias = List.copyOf(intermediarias);
    }
}
