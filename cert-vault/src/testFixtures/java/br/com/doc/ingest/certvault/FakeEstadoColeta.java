package br.com.doc.ingest.certvault;

import br.com.doc.ingest.domain.adapter.Cursor;
import br.com.doc.ingest.domain.coleta.ChaveColeta;
import br.com.doc.ingest.domain.coleta.ConfirmacaoLote;
import br.com.doc.ingest.domain.coleta.EstadoColetaRepositorio;
import br.com.doc.ingest.domain.coleta.PosicaoColeta;
import br.com.doc.ingest.domain.coleta.ResultadoCas;
import br.com.doc.ingest.domain.coleta.ResultadoTroca;
import br.com.doc.ingest.domain.coleta.VinculoCertificado;
import br.com.doc.ingest.domain.model.Cnpj;
import br.com.doc.ingest.domain.model.Nsu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** {@link EstadoColetaRepositorio} em memória, com o mesmo CAS do Jdbi; o ledger não é guardado. */
public final class FakeEstadoColeta implements EstadoColetaRepositorio {

    private static final class Estado {
        Cursor cursor = Cursor.inicial();
        Nsu ultNsu = new Nsu(0);
        long versao;
        Long certificadoId;
    }

    private final Map<ChaveColeta, Estado> estados = new LinkedHashMap<>();
    private Consumer<ChaveColeta> antesDaTroca = chave -> { };

    /** Roda antes de cada {@code trocarCertificado}; serve para simular outra rotação que passou na frente. */
    public void antesDaTroca(Consumer<ChaveColeta> acao) {
        this.antesDaTroca = acao;
    }

    /** Define o certificado direto, como faria outro worker. */
    public synchronized void definirCertificado(ChaveColeta chave, Long certificadoId) {
        estados.get(chave).certificadoId = certificadoId;
    }

    @Override
    public synchronized void criarSeAusente(ChaveColeta chave) {
        estados.putIfAbsent(chave, new Estado());
    }

    @Override
    public synchronized Optional<PosicaoColeta> carregar(ChaveColeta chave) {
        Estado e = estados.get(chave);
        return e == null ? Optional.empty() : Optional.of(new PosicaoColeta(chave, e.cursor, e.ultNsu, e.versao));
    }

    @Override
    public synchronized ResultadoCas confirmarLote(ConfirmacaoLote confirmacao) {
        Estado e = estados.get(confirmacao.lida().chave());
        if (e.versao != confirmacao.lida().versao()) {
            return new ResultadoCas.ConflitoDeVersao(e.versao);
        }
        if (confirmacao.novoUltNsu().ehMenorQue(e.ultNsu)) {
            return new ResultadoCas.RetrocessoRecusado(e.ultNsu);
        }
        e.cursor = confirmacao.novoCursor();
        e.ultNsu = confirmacao.novoUltNsu();
        e.versao++;
        return new ResultadoCas.Aplicado(e.versao);
    }

    @Override
    public ResultadoTroca trocarCertificado(ChaveColeta chave, Long esperado, long novo) {
        antesDaTroca.accept(chave);
        synchronized (this) {
            Estado e = estados.get(chave);
            if (e == null) {
                return new ResultadoTroca.NaoEncontrada();
            }
            if (!Objects.equals(e.certificadoId, esperado)) {
                return new ResultadoTroca.Conflito(e.certificadoId);
            }
            if (e.certificadoId != null && e.certificadoId == novo) {
                return new ResultadoTroca.SemMudanca();
            }
            e.certificadoId = novo;
            return new ResultadoTroca.Trocado();
        }
    }

    @Override
    public synchronized List<VinculoCertificado> certificadosDoCnpj(Cnpj cnpj) {
        List<VinculoCertificado> vinculos = new ArrayList<>();
        estados.forEach((chave, e) -> {
            if (chave.cnpj().equals(cnpj)) {
                vinculos.add(new VinculoCertificado(chave, e.certificadoId));
            }
        });
        return vinculos;
    }
}
