package barbearia;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public class Agenda {
    public static final List<Servico> SERVICOS = List.of(
        new Servico("corte", "Corte de cabelo", "Do clássico ao degradê, no seu estilo.", 45, 45),
        new Servico("barba", "Barba na navalha", "Desenho, alinhamento e toalha quente.", 30, 30),
        new Servico("combo", "Corte + barba", "O cuidado completo, sem pressa.", 75, 70),
        new Servico("acabamento", "Acabamento", "Os detalhes que fazem a diferença.", 15, 20)
    );

    public static final List<Barbeiro> BARBEIROS = List.of(
        new Barbeiro("lucas", "Lucas", "Clássicos & tesoura", "LC"),
        new Barbeiro("rafael", "Rafael", "Degradê & navalha", "RF")
    );

    private final Banco banco;
    private final Clock relogio;
    private final SecureRandom sorteio = new SecureRandom();

    public Agenda(Banco banco, Clock relogio) {
        this.banco = banco;
        this.relogio = relogio;
    }

    public LocalDate hoje() {
        return LocalDate.now(relogio);
    }

    public LocalDate primeiroDia() {
        LocalDate dia = hoje();
        if (dia.getDayOfWeek() == DayOfWeek.SUNDAY) dia = dia.plusDays(1);
        return dia;
    }

    public static Servico servico(String id) {
        return SERVICOS.stream().filter(item -> item.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Escolha um serviço da lista."));
    }

    public static Barbeiro barbeiro(String id) {
        return BARBEIROS.stream().filter(item -> item.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Escolha um dos profissionais."));
    }

    public void validarDia(LocalDate dia) {
        if (dia.isBefore(hoje()) || dia.isAfter(hoje().plusDays(60))) {
            throw new IllegalArgumentException("Escolha uma data entre hoje e os próximos 60 dias.");
        }
    }

    public List<LocalTime> horarios(String servicoId, String barbeiroId, LocalDate dia) throws SQLException {
        Servico servico = servico(servicoId);
        barbeiro(barbeiroId);
        validarDia(dia);
        List<LocalTime> livres = new ArrayList<>();
        if (dia.getDayOfWeek() == DayOfWeek.SUNDAY) return livres;

        List<LocalTime> ocupados = banco.horariosOcupados(barbeiroId, dia);
        LocalTime fechamento = dia.getDayOfWeek() == DayOfWeek.SATURDAY
            ? LocalTime.of(16, 0) : LocalTime.of(19, 0);

        for (LocalTime inicio = LocalTime.of(9, 0); !inicio.plusMinutes(servico.minutos()).isAfter(fechamento);
             inicio = inicio.plusMinutes(15)) {
            LocalTime fim = inicio.plusMinutes(servico.minutos());
            if (inicio.isBefore(LocalTime.of(13, 0)) && fim.isAfter(LocalTime.NOON)) continue;
            if (!LocalDateTime.of(dia, inicio).isAfter(LocalDateTime.now(relogio).plusMinutes(15))) continue;
            boolean disponivel = true;
            for (int minuto = 0; minuto < servico.minutos(); minuto += 15) {
                if (ocupados.contains(inicio.plusMinutes(minuto))) disponivel = false;
            }
            if (disponivel) livres.add(inicio);
        }
        return livres;
    }

    public String agendar(String servicoId, String barbeiroId, LocalDate dia, LocalTime horario,
                          String nome, String telefone) throws SQLException {
        nome = nome == null ? "" : nome.strip().replaceAll("\\s+", " ");
        telefone = limparTelefone(telefone);
        if (nome.length() < 3 || nome.length() > 80 || nome.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Informe um nome entre 3 e 80 caracteres.");
        }
        if (telefone.length() < 10 || telefone.length() > 11) {
            throw new IllegalArgumentException("Informe um telefone com DDD, com 10 ou 11 números.");
        }
        if (!horarios(servicoId, barbeiroId, dia).contains(horario)) {
            throw new IllegalArgumentException("Esse horário não está disponível. Escolha outro na agenda.");
        }
        byte[] bytes = new byte[8];
        sorteio.nextBytes(bytes);
        String codigo = HexFormat.of().withUpperCase().formatHex(bytes);
        Agendamento agendamento = new Agendamento(codigo, servicoId, barbeiroId, dia,
            horario, nome, telefone, false);
        banco.salvar(agendamento, servico(servicoId).minutos());
        return codigo;
    }

    public Agendamento consultar(String codigo, String telefone) throws SQLException {
        codigo = codigo == null ? "" : codigo.strip().replace("-", "").toUpperCase();
        telefone = limparTelefone(telefone);
        Agendamento encontrado = codigo.matches("[A-F0-9]{16}") ? banco.buscar(codigo) : null;
        if (encontrado == null || !encontrado.telefone().equals(telefone)) {
            throw new IllegalArgumentException("Não encontramos uma reserva com esse código e telefone.");
        }
        return encontrado;
    }

    public boolean podeCancelar(Agendamento agendamento) {
        return !agendamento.cancelado()
            && LocalDateTime.of(agendamento.dia(), agendamento.horario()).isAfter(LocalDateTime.now(relogio));
    }

    public void cancelar(String codigo, String telefone) throws SQLException {
        Agendamento agendamento = consultar(codigo, telefone);
        if (agendamento.cancelado()) return;
        if (!podeCancelar(agendamento)) throw new IllegalArgumentException("Esse atendimento já começou ou passou.");
        banco.cancelar(agendamento.codigo());
    }

    public static String limparTelefone(String telefone) {
        return telefone == null ? "" : telefone.replaceAll("[^0-9]", "");
    }
}
