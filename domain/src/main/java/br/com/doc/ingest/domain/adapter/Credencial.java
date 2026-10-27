package br.com.doc.ingest.domain.adapter;

import java.util.Arrays;

/**
 * Credencial para autenticar na origem: bytes do certificado A1 (.pfx) + senha.
 *
 * <p>Vive só em memória: é entregue pelo cofre no momento do uso, nunca aponta
 * para um caminho de disco (CLAUDE.md §7.4 e §9; auditoria A6). Os arrays são
 * copiados na entrada e nos acessores, e {@link #limpar()} zera o conteúdo
 * interno depois do uso. {@link #toString()} não expõe segredo (invariante 13).
 *
 * @param pfx   bytes do arquivo PKCS#12 (não vazio)
 * @param senha senha do .pfx (pode ser vazia, nunca nula)
 */
public record Credencial(byte[] pfx, char[] senha) {

    public Credencial {
        if (pfx == null || pfx.length == 0) {
            throw new IllegalArgumentException("pfx não pode ser nulo nem vazio");
        }
        if (senha == null) {
            throw new IllegalArgumentException("senha não pode ser nula");
        }
        pfx = pfx.clone();
        senha = senha.clone();
    }

    /** @return cópia dos bytes do .pfx */
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
     * Zera os bytes do .pfx e a senha guardados nesta credencial.
     */
    public void limpar() {
        Arrays.fill(pfx, (byte) 0);
        Arrays.fill(senha, '\0');
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Credencial outra
                && Arrays.equals(pfx, outra.pfx)
                && Arrays.equals(senha, outra.senha);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(pfx) + Arrays.hashCode(senha);
    }

    @Override
    public String toString() {
        return "Credencial[pfx=" + pfx.length + " bytes, senha=***]";
    }
}
