package br.com.doc.ingest.domain.cofre;

import br.com.doc.ingest.domain.model.Cnpj;

import java.util.Arrays;

/**
 * Pedido de guarda de um certificado A1: bytes do .pfx + senha, ainda não validados.
 *
 * <p>Vive só em memória. Os arrays são copiados na entrada e nos acessores, e {@link #limpar()}
 * zera o conteúdo interno depois do uso. {@link #toString()} não expõe segredo (invariante 13).
 *
 * @param tenantId         tenant dono do certificado (positivo)
 * @param titularEmpresaId empresa titular do certificado (positivo)
 * @param cnpjTitular      CNPJ que o certificado precisa trazer
 * @param pfx              bytes do arquivo PKCS#12 (não vazio)
 * @param senha            senha do .pfx (pode ser vazia, nunca nula)
 * @param enviadoPor       quem enviou, para a trilha
 */
public record PedidoGuarda(long tenantId, long titularEmpresaId, Cnpj cnpjTitular,
                           byte[] pfx, char[] senha, String enviadoPor) {

    public PedidoGuarda {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId deve ser positivo");
        }
        if (titularEmpresaId <= 0) {
            throw new IllegalArgumentException("titularEmpresaId deve ser positivo");
        }
        if (cnpjTitular == null) {
            throw new IllegalArgumentException("cnpjTitular não pode ser nulo");
        }
        if (pfx == null || pfx.length == 0) {
            throw new IllegalArgumentException("pfx não pode ser nulo nem vazio");
        }
        if (senha == null) {
            throw new IllegalArgumentException("senha não pode ser nula");
        }
        if (enviadoPor == null || enviadoPor.isBlank()) {
            throw new IllegalArgumentException("enviadoPor não pode ser nulo nem em branco");
        }
        pfx = pfx.clone();
        senha = senha.clone();
    }

    /** @return cópia dos bytes do .pfx; quem chama deve zerá-la após o uso */
    @Override
    public byte[] pfx() {
        return pfx.clone();
    }

    /** @return cópia da senha; quem chama deve zerá-la após o uso */
    @Override
    public char[] senha() {
        return senha.clone();
    }

    /**
     * Zera os bytes do .pfx e a senha guardados neste pedido.
     */
    public void limpar() {
        Arrays.fill(pfx, (byte) 0);
        Arrays.fill(senha, '\0');
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PedidoGuarda outro
                && tenantId == outro.tenantId
                && titularEmpresaId == outro.titularEmpresaId
                && cnpjTitular.equals(outro.cnpjTitular)
                && Arrays.equals(pfx, outro.pfx)
                && Arrays.equals(senha, outro.senha)
                && enviadoPor.equals(outro.enviadoPor);
    }

    @Override
    public int hashCode() {
        int resultado = Long.hashCode(tenantId);
        resultado = 31 * resultado + Long.hashCode(titularEmpresaId);
        resultado = 31 * resultado + cnpjTitular.hashCode();
        resultado = 31 * resultado + Arrays.hashCode(pfx);
        resultado = 31 * resultado + Arrays.hashCode(senha);
        return 31 * resultado + enviadoPor.hashCode();
    }

    @Override
    public String toString() {
        return "PedidoGuarda[tenantId=" + tenantId + ", titularEmpresaId=" + titularEmpresaId
                + ", pfx=" + pfx.length + " bytes, senha=***]";
    }
}
