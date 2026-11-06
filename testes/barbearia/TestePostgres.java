package barbearia;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class TestePostgres {
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-09T11:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private static final LocalDate DIA = LocalDate.of(2026, 11, 9);
    private static String endereco;
    private static int aprovados;

    public static void main(String[] argumentos) throws Exception {
        endereco = System.getenv("TEST_DATABASE_URL");
        if (endereco == null) throw new IllegalArgumentException("Informe TEST_DATABASE_URL para executar os testes PostgreSQL.");
        URI url = URI.create(endereco);
        if (!("localhost".equals(url.getHost()) || "127.0.0.1".equals(url.getHost()))
            || !"/fl_barbearia_teste".equals(url.getPath())) {
            throw new IllegalArgumentException("Os testes só podem usar o banco local fl_barbearia_teste.");
        }
        Banco banco = Configuracao.bancoPostgres(endereco);
        try {
            limpar();
            Agenda agenda = new Agenda(banco, RELOGIO);
            String codigo = reservar(agenda);
            Agendamento reserva = agenda.consultar(codigo, "19999991234");
            conferir("PostgreSQL preserva data, horário e dados da reserva", reserva.dia().equals(DIA)
                && reserva.horario().equals(LocalTime.of(9, 0)) && reserva.nome().equals("Pessoa de teste"));
            Agenda reaberta = new Agenda(Configuracao.bancoPostgres(endereco), RELOGIO);
            conferir("Reabrir a conexão mantém a reserva", reaberta.consultar(codigo, "19999991234").codigo().equals(codigo));
            conferir("Toda a duração fica indisponível", !agenda.horarios("acabamento", "lucas", DIA).contains(LocalTime.of(10, 0))
                && agenda.horarios("acabamento", "lucas", DIA).contains(LocalTime.of(10, 15)));
            try {
                banco.salvar(new Agendamento("AAAAAAAAAAAAAAAA", "combo", "lucas", DIA,
                    LocalTime.of(9, 0), "Outro cliente", "19999990000", false), 75);
                throw new AssertionError("Uma reserva duplicada foi aceita.");
            } catch (IllegalArgumentException erro) {
                conferir("Conflito no banco desfaz a reserva inteira", banco.buscar("AAAAAAAAAAAAAAAA") == null && contar("agendamentos") == 1 && contar("horarios_ocupados") == 5);
            }
            agenda.cancelar(codigo, "19999991234");
            conferir("Cancelamento fica salvo e libera todos os intervalos", agenda.consultar(codigo, "19999991234").cancelado()
                && contar("horarios_ocupados") == 0 && agenda.horarios("combo", "lucas", DIA).contains(LocalTime.of(9, 0)));
            limpar();
            CountDownLatch largada = new CountDownLatch(1);
            AtomicInteger confirmados = new AtomicInteger();
            try (var tarefas = Executors.newFixedThreadPool(2)) {
                var primeira = tarefas.submit(() -> disputar(largada, confirmados));
                var segunda = tarefas.submit(() -> disputar(largada, confirmados));
                largada.countDown();
                primeira.get();
                segunda.get();
            }
            conferir("PostgreSQL impede duas confirmações simultâneas", confirmados.get() == 1 && contar("agendamentos") == 1 && contar("horarios_ocupados") == 5);
            conferir("Outro profissional mantém os horários disponíveis", agenda.horarios("combo", "rafael", DIA).contains(LocalTime.of(9, 0)));
        } finally {
            limpar();
        }
        System.out.println(aprovados + " testes PostgreSQL aprovados.");
    }

    private static String reservar(Agenda agenda) throws Exception {
        return agenda.agendar("combo", "lucas", DIA, LocalTime.of(9, 0), "Pessoa de teste", "19999991234");
    }

    private static void disputar(CountDownLatch largada, AtomicInteger confirmados) {
        try {
            largada.await();
            reservar(new Agenda(Configuracao.bancoPostgres(endereco), RELOGIO));
            confirmados.incrementAndGet();
        } catch (IllegalArgumentException erro) {
            if (!erro.getMessage().contains("horário")) throw erro;
        } catch (Exception erro) {
            throw new RuntimeException(erro);
        }
    }

    private static Connection conectar() throws Exception {
        URI url = URI.create(endereco);
        String[] acesso = url.getRawUserInfo().split(":", 2);
        String usuario = java.net.URLDecoder.decode(acesso[0], java.nio.charset.StandardCharsets.UTF_8);
        String senha = java.net.URLDecoder.decode(acesso[1].replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
        int porta = url.getPort() == -1 ? 5432 : url.getPort();
        return DriverManager.getConnection("jdbc:postgresql://" + url.getHost() + ":" + porta + url.getPath(), usuario, senha);
    }

    private static void limpar() throws Exception {
        try (Connection conexao = conectar(); var comando = conexao.createStatement()) {
            comando.executeUpdate("DELETE FROM horarios_ocupados");
            comando.executeUpdate("DELETE FROM agendamentos");
        }
    }

    private static int contar(String tabela) throws Exception {
        if (!tabela.equals("agendamentos") && !tabela.equals("horarios_ocupados")) throw new IllegalArgumentException();
        try (Connection conexao = conectar(); var comando = conexao.createStatement(); var linhas = comando.executeQuery("SELECT COUNT(*) FROM " + tabela)) {
            linhas.next();
            return linhas.getInt(1);
        }
    }

    private static void conferir(String nome, boolean condicao) {
        if (!condicao) throw new AssertionError(nome);
        aprovados++;
        System.out.println("OK · " + nome);
    }
}
