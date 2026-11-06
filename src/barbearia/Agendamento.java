package barbearia;

import java.time.LocalDate;
import java.time.LocalTime;

public record Agendamento(String codigo, String servico, String barbeiro, LocalDate dia,
                          LocalTime horario, String nome, String telefone, boolean cancelado) {
}
