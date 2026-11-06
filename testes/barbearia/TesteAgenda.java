package barbearia;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class TesteAgenda {
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-09T11:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 11, 9);
    private static int aprovados;

    public static void main(String[] argumentos) throws Exception {
        testar("Abre às 9h nos dias úteis", () -> conferir(novaAgenda().horarios("corte", "lucas", SEGUNDA).getFirst().equals(LocalTime.of(9, 0))));
        testar("Corte termina antes do fechamento", () -> conferir(novaAgenda().horarios("corte", "lucas", SEGUNDA).getLast().equals(LocalTime.of(18, 15))));
        testar("Não atravessa o almoço", () -> {
            var horarios = novaAgenda().horarios("combo", "lucas", SEGUNDA);
            conferir(horarios.contains(LocalTime.of(10, 45)));
            conferir(!horarios.contains(LocalTime.of(11, 0)));
            conferir(!horarios.contains(LocalTime.NOON));
            conferir(horarios.contains(LocalTime.of(13, 0)));
        });
        testar("Sábado fecha às 16h", () -> conferir(novaAgenda().horarios("corte", "lucas", SEGUNDA.plusDays(5)).getLast().equals(LocalTime.of(15, 15))));
        testar("Domingo não tem atendimento", () -> conferir(novaAgenda().horarios("corte", "lucas", SEGUNDA.plusDays(6)).isEmpty()));
        testar("Recusa datas passadas", () -> recusar(() -> novaAgenda().horarios("corte", "lucas", SEGUNDA.minusDays(1))));
        testar("Recusa datas além dos 60 dias", () -> recusar(() -> novaAgenda().horarios("corte", "lucas", SEGUNDA.plusDays(61))));
        testar("Aceita o limite de 60 dias", () -> conferir(!novaAgenda().horarios("corte", "lucas", SEGUNDA.plusDays(60)).isEmpty()));
        testar("Exige mais de 15 minutos de antecedência", () -> {
            Banco banco = novoBanco();
            Clock tarde = Clock.fixed(Instant.parse("2026-11-09T17:00:00Z"), RELOGIO.getZone());
            var horarios = new Agenda(banco, tarde).horarios("acabamento", "lucas", SEGUNDA);
            conferir(!horarios.contains(LocalTime.of(14, 15)));
            conferir(horarios.contains(LocalTime.of(14, 30)));
        });
        testar("Salva e consulta uma reserva", () -> {
            Agenda agenda = novaAgenda();
            String codigo = reservar(agenda, "corte", "lucas", LocalTime.of(9, 0));
            var reserva = agenda.consultar(codigo, "(19) 99999-1234");
            conferir(reserva.nome().equals("Cliente de teste"));
            conferir(reserva.servico().equals("corte"));
            conferir(codigo.matches("[A-F0-9]{16}"));
        });
        testar("Reserva bloqueia toda a duração", () -> {
            Agenda agenda = novaAgenda();
            reservar(agenda, "combo", "lucas", LocalTime.of(9, 0));
            var horarios = agenda.horarios("acabamento", "lucas", SEGUNDA);
            conferir(!horarios.contains(LocalTime.of(10, 0)));
            conferir(horarios.contains(LocalTime.of(10, 15)));
        });
        testar("Recusa serviços que começam antes de uma reserva e se sobrepõem", () -> {
            Agenda agenda = novaAgenda();
            reservar(agenda, "barba", "lucas", LocalTime.of(10, 0));
            recusar(() -> reservar(agenda, "combo", "lucas", LocalTime.of(9, 0)));
        });
        testar("Profissionais têm agendas independentes", () -> {
            Agenda agenda = novaAgenda();
            reservar(agenda, "corte", "lucas", LocalTime.of(9, 0));
            conferir(agenda.horarios("corte", "rafael", SEGUNDA).contains(LocalTime.of(9, 0)));
        });
        testar("Recusa dois atendimentos no mesmo intervalo", () -> {
            Agenda agenda = novaAgenda();
            reservar(agenda, "corte", "lucas", LocalTime.of(9, 0));
            recusar(() -> reservar(agenda, "corte", "lucas", LocalTime.of(9, 15)));
        });
        testar("Recusa horário fora da grade", () -> recusar(() -> reservar(novaAgenda(), "corte", "lucas", LocalTime.of(9, 7))));
        testar("Valida nome, telefone, serviço e profissional", () -> {
            Agenda agenda = novaAgenda();
            recusar(() -> agenda.agendar("corte", "lucas", SEGUNDA, LocalTime.of(9, 0), "A", "19999991234"));
            recusar(() -> agenda.agendar("corte", "lucas", SEGUNDA, LocalTime.of(9, 0), "Nome", "123"));
            recusar(() -> reservar(agenda, "inventado", "lucas", LocalTime.of(9, 0)));
            recusar(() -> reservar(agenda, "corte", "inventado", LocalTime.of(9, 0)));
        });
        testar("Não consulta com telefone ou código incorretos", () -> {
            Agenda agenda = novaAgenda();
            String codigo = reservar(agenda, "corte", "lucas", LocalTime.of(9, 0));
            recusar(() -> agenda.consultar(codigo, "19999990000"));
            recusar(() -> agenda.consultar("0000000000000000", "19999991234"));
        });
        testar("Cancelar exige o telefone correto", () -> {
            Agenda agenda = novaAgenda();
            String codigo = reservar(agenda, "corte", "lucas", LocalTime.of(9, 0));
            recusar(() -> agenda.cancelar(codigo, "19999990000"));
            conferir(!agenda.consultar(codigo, "19999991234").cancelado());
        });
        testar("Cancelamento libera todo o intervalo", () -> {
            Agenda agenda = novaAgenda();
            String codigo = reservar(agenda, "combo", "lucas", LocalTime.of(9, 0));
            agenda.cancelar(codigo, "19999991234");
            conferir(agenda.consultar(codigo, "19999991234").cancelado());
            conferir(agenda.horarios("combo", "lucas", SEGUNDA).contains(LocalTime.of(9, 0)));
            agenda.cancelar(codigo, "19999991234");
        });
        testar("Atendimento passado não pode ser cancelado", () -> {
            Banco banco = novoBanco();
            String codigo = reservar(new Agenda(banco, RELOGIO), "corte", "lucas", LocalTime.of(9, 0));
            Agenda depois = new Agenda(banco, Clock.offset(RELOGIO, java.time.Duration.ofDays(1)));
            recusar(() -> depois.cancelar(codigo, "19999991234"));
        });
        testar("Consultas preparadas preservam textos sem executar SQL", () -> {
            Agenda agenda = novaAgenda();
            String nome = "Cliente'); DROP TABLE agendamentos; --";
            String codigo = agenda.agendar("corte", "lucas", SEGUNDA, LocalTime.of(9, 0), nome, "19999991234");
            conferir(agenda.consultar(codigo, "19999991234").nome().equals(nome));
        });
        testar("Escapa texto antes de colocar na página", () -> conferir(Pagina.escapar("<script>\"&'").equals("&lt;script&gt;&quot;&amp;&#39;")));
        testar("Dados continuam salvos ao reabrir o banco", () -> {
            Path pasta = Files.createTempDirectory("fl-barbearia-teste-");
            String endereco = "jdbc:h2:file:" + pasta.resolve("agenda").toString().replace('\\', '/');
            String codigo = reservar(new Agenda(new Banco(endereco), RELOGIO), "corte", "lucas", LocalTime.of(9, 0));
            Agenda reaberta = new Agenda(new Banco(endereco), RELOGIO);
            conferir(reaberta.consultar(codigo, "19999991234").horario().equals(LocalTime.of(9, 0)));
            Files.deleteIfExists(pasta.resolve("agenda.mv.db"));
            Files.delete(pasta);
        });
        testar("Duas reservas simultâneas deixam apenas uma confirmação", TesteAgenda::concorrencia);
        System.out.println("\n" + aprovados + " testes aprovados.");
    }

    private static void concorrencia() throws Exception {
        String endereco = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Banco banco = new Banco(endereco);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger confirmados = new AtomicInteger();
        try (var tarefas = Executors.newFixedThreadPool(2)) {
            var primeira = tarefas.submit(() -> disputar(banco, largada, confirmados));
            var segunda = tarefas.submit(() -> disputar(banco, largada, confirmados));
            largada.countDown();
            primeira.get();
            segunda.get();
        }
        conferir(confirmados.get() == 1);
        try (var conexao = DriverManager.getConnection(endereco, "sa", ""); var comando = conexao.createStatement()) {
            try (var linhas = comando.executeQuery("SELECT COUNT(*) FROM agendamentos")) {
                linhas.next();
                conferir(linhas.getInt(1) == 1);
            }
            try (var linhas = comando.executeQuery("SELECT COUNT(*) FROM horarios_ocupados")) {
                linhas.next();
                conferir(linhas.getInt(1) == 5);
            }
        }
    }

    private static void disputar(Banco banco, CountDownLatch largada, AtomicInteger confirmados) {
        try {
            largada.await();
            reservar(new Agenda(banco, RELOGIO), "combo", "lucas", LocalTime.of(9, 0));
            confirmados.incrementAndGet();
        } catch (IllegalArgumentException erro) {
            conferir(erro.getMessage().contains("horário"));
        } catch (Exception erro) {
            throw new RuntimeException(erro);
        }
    }

    private static Banco novoBanco() throws Exception {
        return new Banco("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    }

    private static Agenda novaAgenda() throws Exception {
        return new Agenda(novoBanco(), RELOGIO);
    }

    private static String reservar(Agenda agenda, String servico, String barbeiro, LocalTime horario) throws Exception {
        return agenda.agendar(servico, barbeiro, SEGUNDA, horario, "Cliente de teste", "19999991234");
    }

    private static void conferir(boolean condicao) {
        if (!condicao) throw new AssertionError("O resultado não foi o esperado.");
    }

    private static void recusar(Verificacao acao) throws Exception {
        try { acao.executar(); }
        catch (IllegalArgumentException erro) { return; }
        throw new AssertionError("A entrada inválida foi aceita.");
    }

    private static void testar(String nome, Verificacao verificacao) throws Exception {
        verificacao.executar();
        aprovados++;
        System.out.println("OK · " + nome);
    }

    private interface Verificacao {
        void executar() throws Exception;
    }
}
