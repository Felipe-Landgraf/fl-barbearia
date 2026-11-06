package barbearia;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Pagina {
    private static final Locale BRASIL = Locale.forLanguageTag("pt-BR");
    private final Path pasta;
    private final Agenda agenda;
    private final String token;

    public Pagina(Path pasta, Agenda agenda, String token) {
        this.pasta = pasta;
        this.agenda = agenda;
        this.token = token;
    }

    public String inicio(Map<String, String> parametros, String aviso) throws IOException, SQLException {
        Servico servico = Agenda.servico(parametros.getOrDefault("servico", "corte"));
        Barbeiro barbeiro = Agenda.barbeiro(parametros.getOrDefault("barbeiro", "lucas"));
        LocalDate dia = LocalDate.parse(parametros.getOrDefault("data", agenda.primeiroDia().toString()));
        agenda.validarDia(dia);
        List<LocalTime> horarios = agenda.horarios(servico.id(), barbeiro.id(), dia);
        Map<String, String> dados = new HashMap<>();
        StringBuilder cards = new StringBuilder();
        StringBuilder opcoes = new StringBuilder();
        int numero = 1;
        for (Servico item : Agenda.SERVICOS) {
            String link = "/?servico=" + item.id() + "&amp;barbeiro=" + barbeiro.id() + "&amp;data=" + dia + "#agendar";
            cards.append("<article class='servico'><span class='numero'>0").append(numero++)
                .append("</span><h3>").append(item.nome()).append("</h3><p>").append(item.descricao())
                .append("</p><div><span>").append(item.minutos()).append(" min</span><strong>")
                .append(dinheiro(item.preco())).append("</strong></div><a href='").append(link)
                .append("' aria-label='Agendar ").append(item.nome()).append("'>Escolher serviço <span aria-hidden='true'>↗</span></a></article>");
            opcoes.append("<label class='escolha'><input type='radio' name='servico' value='").append(item.id())
                .append("' required ").append(item.id().equals(servico.id()) ? "checked" : "")
                .append("><span><strong>").append(item.nome()).append("</strong><small>")
                .append(item.minutos()).append(" min · ").append(dinheiro(item.preco())).append("</small></span></label>");
        }
        StringBuilder profissionais = new StringBuilder();
        for (Barbeiro item : Agenda.BARBEIROS) {
            profissionais.append("<label class='profissional escolha'><input type='radio' name='barbeiro' value='")
                .append(item.id()).append("' required ").append(item.id().equals(barbeiro.id()) ? "checked" : "")
                .append("><span class='iniciais' aria-hidden='true'>").append(item.iniciais()).append("</span><span><strong>")
                .append(item.nome()).append("</strong><small>").append(item.especialidade()).append("</small></span></label>");
        }
        StringBuilder horas = new StringBuilder();
        for (LocalTime horario : horarios) {
            horas.append("<label class='hora'><input type='radio' name='horario' value='").append(horario)
                .append("' required ").append(horario.toString().equals(parametros.get("horario")) ? "checked" : "")
                .append("><span>").append(horario).append("</span></label>");
        }
        if (horarios.isEmpty()) {
            horas.append("<p class='sem-horarios'>").append(dia.getDayOfWeek().getValue() == 7
                ? "Domingo é dia de descanso. Escolha outro dia para o seu atendimento."
                : "Nenhum horário disponível para essa combinação. Tente outro dia, serviço ou profissional.").append("</p>");
        }
        dados.put("cards", cards.toString());
        dados.put("servicos", opcoes.toString());
        dados.put("barbeiros", profissionais.toString());
        dados.put("horarios", horas.toString());
        dados.put("data", dia.toString());
        dados.put("minimo", agenda.hoje().toString());
        dados.put("maximo", agenda.hoje().plusDays(60).toString());
        dados.put("servico_id", servico.id());
        dados.put("barbeiro_id", barbeiro.id());
        dados.put("servico_nome", servico.nome());
        dados.put("barbeiro_nome", barbeiro.nome());
        dados.put("dia_extenso", escapar(dia.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", BRASIL))));
        dados.put("duracao", Integer.toString(servico.minutos()));
        dados.put("preco", dinheiro(servico.preco()));
        dados.put("aviso", aviso(aviso));
        dados.put("token", token);
        dados.put("nome", escapar(parametros.getOrDefault("nome", "")));
        dados.put("telefone", escapar(parametros.getOrDefault("telefone", "")));
        dados.put("desabilitado", horarios.isEmpty() ? "disabled" : "");
        dados.put("quantidade", horarios.size() + (horarios.size() == 1 ? " horário disponível" : " horários disponíveis"));
        return layout("FL Barbearia — Seu estilo, sua assinatura", preencher("inicio.html", dados));
    }

    public String consulta(String codigo, String aviso) throws IOException {
        return layout("Meu agendamento — FL Barbearia", preencher("consulta.html", Map.of(
            "codigo", escapar(codigo), "aviso", aviso(aviso), "token", token)));
    }

    public String reserva(Agendamento reserva, String telefone, boolean nova) throws IOException {
        Servico servico = Agenda.servico(reserva.servico());
        Barbeiro barbeiro = Agenda.barbeiro(reserva.barbeiro());
        String acao;
        if (reserva.cancelado()) {
            acao = "<div class='mensagem neutra'>O horário foi liberado. Quando quiser, será um prazer receber você de novo.</div><a class='botao' href='/#agendar'>Escolher um novo horário <span aria-hidden='true'>↗</span></a>";
        } else if (!agenda.podeCancelar(reserva)) {
            acao = "<p class='mensagem neutra'>A data deste atendimento já passou ou o atendimento já começou.</p><a class='botao' href='/#agendar'>Agendar novamente <span aria-hidden='true'>↗</span></a>";
        } else if (telefone.isBlank()) {
            acao = "<a class='botao contorno' href='/consultar?codigo=" + reserva.codigo() + "'>Consultar ou cancelar reserva</a>";
        } else {
            acao = "<form action='/cancelar' method='post' id='form-cancelar'><input type='hidden' name='token' value='" + token
                + "'><input type='hidden' name='codigo' value='" + reserva.codigo()
                + "'><input type='hidden' name='telefone' value='" + escapar(telefone)
                + "'><label class='confirmar-cancelamento'><input type='checkbox' required name='confirmacao' value='sim'> Quero cancelar este agendamento e liberar o horário.</label><button class='botao perigo' type='submit'>Cancelar agendamento</button></form>";
        }
        return layout("Seu agendamento — FL Barbearia", preencher("reserva.html", Map.ofEntries(
            Map.entry("titulo", reserva.cancelado() ? "Agendamento cancelado." : nova ? "Seu horário está reservado." : "Tudo sobre seu horário."),
            Map.entry("estado", reserva.cancelado() ? "CANCELADO" : agenda.podeCancelar(reserva) ? "CONFIRMADO" : "ATENDIMENTO PASSADO"),
            Map.entry("simbolo", reserva.cancelado() ? "×" : "✓"),
            Map.entry("servico", servico.nome()), Map.entry("barbeiro", barbeiro.nome()),
            Map.entry("data", escapar(reserva.dia().format(DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", BRASIL)))),
            Map.entry("horario", reserva.horario().toString()), Map.entry("fim", reserva.horario().plusMinutes(servico.minutos()).toString()),
            Map.entry("preco", dinheiro(servico.preco())), Map.entry("codigo", reserva.codigo()),
            Map.entry("acao", acao))));
    }

    public String erro(String aviso) throws IOException {
        String conteudo = "<section class='pagina-curta'><p class='etiqueta'>UM PEQUENO AJUSTE</p><h1>Vamos tentar de novo?</h1>"
            + aviso(aviso) + "<a class='botao' href='/#agendar'>Voltar para a agenda <span aria-hidden='true'>↗</span></a></section>";
        return layout("Confira os dados — FL Barbearia", conteudo);
    }

    private String layout(String titulo, String conteudo) throws IOException {
        return preencher("layout.html", Map.of("titulo", titulo, "conteudo", conteudo));
    }

    private String preencher(String arquivo, Map<String, String> dados) throws IOException {
        String modelo = Files.readString(pasta.resolve(arquivo), StandardCharsets.UTF_8);
        Matcher campos = Pattern.compile("\\{\\{([a-z_]+)}}").matcher(modelo);
        return campos.replaceAll(campo -> Matcher.quoteReplacement(dados.getOrDefault(campo.group(1), "")));
    }

    public static String escapar(String valor) {
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String aviso(String texto) {
        return texto == null || texto.isBlank() ? "" : "<div class='mensagem' role='alert'>" + escapar(texto) + "</div>";
    }

    private static String dinheiro(int valor) {
        return NumberFormat.getCurrencyInstance(BRASIL).format(valor);
    }
}
