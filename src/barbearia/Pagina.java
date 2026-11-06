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

    public String inicio(List<Agendamento> reservas) throws IOException {
        StringBuilder cards = new StringBuilder();
        int numero = 1;
        for (Servico item : Agenda.SERVICOS) {
            cards.append("<article class='servico'><span class='numero'>0").append(numero++)
                .append("</span><h3>").append(item.nome()).append("</h3><p>").append(item.descricao())
                .append("</p><div><span>").append(item.minutos()).append(" min</span><strong>")
                .append(dinheiro(item.preco())).append("</strong></div><a href='/agendar?servico=")
                .append(item.id()).append("' aria-label='Agendar ").append(item.nome())
                .append("'>Escolher serviço <span aria-hidden='true'>↗</span></a></article>");
        }
        String lembrete = "<div class='atalho-agenda'><div><strong>Já tem um horário?</strong><p>Veja sua reserva em um só lugar.</p></div><a class='botao contorno' href='/consultar'>Meu agendamento <span aria-hidden='true'>↗</span></a></div>";
        for (Agendamento reserva : reservas) {
            if (agenda.podeCancelar(reserva)) {
                lembrete = "<div class='lembrete-reserva'><p class='etiqueta'>SEU AGENDAMENTO</p>" + cartao(reserva) + "</div>";
                break;
            }
        }
        return layout("FL Barbearia — Seu estilo, sua assinatura", preencher("inicio.html", Map.of(
            "cards", cards.toString(), "lembrete", lembrete)), "pagina-inicial");
    }

    public String agendar(Map<String, String> parametros, String aviso) throws IOException, SQLException {
        Servico servico = Agenda.servico(parametros.getOrDefault("servico", "corte"));
        Barbeiro barbeiro = Agenda.barbeiro(parametros.getOrDefault("barbeiro", "lucas"));
        LocalDate dia = LocalDate.parse(parametros.getOrDefault("data", agenda.primeiroDia().toString()));
        agenda.validarDia(dia);
        List<LocalTime> horarios = agenda.horarios(servico.id(), barbeiro.id(), dia);
        Map<String, String> dados = new HashMap<>();
        StringBuilder opcoes = new StringBuilder();
        for (Servico item : Agenda.SERVICOS) {
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
        dados.put("alterar", "/agendar?servico=" + servico.id() + "&amp;barbeiro=" + barbeiro.id() + "&amp;data=" + dia);
        dados.put("horario_selecionado", horarios.contains(LocalTime.parse(parametros.getOrDefault("horario", "00:00")))
            ? parametros.get("horario") : "Escolha um horário acima");
        boolean confirmar = "horarios".equals(parametros.get("etapa"));
        return layout("Agendar horário — FL Barbearia", preencher(confirmar ? "horarios.html" : "agendar.html", dados));
    }

    public String consulta(List<Agendamento> reservas, String codigo, String aviso) throws IOException {
        StringBuilder confirmadas = new StringBuilder();
        StringBuilder anteriores = new StringBuilder();
        for (Agendamento reserva : reservas) {
            if (agenda.podeCancelar(reserva)) confirmadas.append(cartao(reserva));
            else anteriores.append(cartao(reserva));
        }
        String lista = confirmadas.isEmpty()
            ? "<div class='agenda-vazia'><span aria-hidden='true'>◷</span><h2>Nenhum horário marcado por aqui.</h2><p>Depois de reservar, seu agendamento aparece aqui automaticamente neste navegador.</p><a class='botao' href='/agendar'>Agendar um horário <span aria-hidden='true'>↗</span></a></div>"
            : "<div class='lista-reservas'>" + confirmadas + "</div>";
        if (!anteriores.isEmpty()) lista += "<details class='historico'><summary>Anteriores e cancelados</summary><div class='lista-reservas'>" + anteriores + "</div></details>";
        return layout("Meu agendamento — FL Barbearia", preencher("consulta.html", Map.of(
            "reservas", lista, "codigo", escapar(codigo), "aviso", aviso(aviso), "token", token,
            "aberto", !codigo.isBlank() || !aviso.isBlank() ? "open" : "")));
    }

    private String cartao(Agendamento reserva) {
        String estado = reserva.cancelado() ? "Cancelado" : agenda.podeCancelar(reserva) ? "Confirmado" : "Atendimento passado";
        return "<article class='cartao-reserva'><div class='data-reserva'><strong>"
            + reserva.dia().format(DateTimeFormatter.ofPattern("dd", BRASIL)) + "</strong><span>"
            + reserva.dia().format(DateTimeFormatter.ofPattern("MMM", BRASIL)) + "</span></div><div class='cartao-informacoes'><span class='estado-reserva'>"
            + estado + "</span><h2>" + Agenda.servico(reserva.servico()).nome() + "</h2><p>"
            + reserva.dia().format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", BRASIL)) + " · <strong>"
            + reserva.horario() + "</strong></p><p>Com " + Agenda.barbeiro(reserva.barbeiro()).nome()
            + "</p></div><a class='botao contorno' href='/reserva?codigo=" + reserva.codigo()
            + "' aria-label='Ver detalhes de " + Agenda.servico(reserva.servico()).nome() + " em "
            + reserva.dia().format(DateTimeFormatter.ofPattern("dd/MM", BRASIL)) + " às " + reserva.horario()
            + "'>Ver detalhes <span aria-hidden='true'>↗</span></a></article>";
    }

    public String reserva(Agendamento reserva, String telefone, boolean nova) throws IOException {
        Servico servico = Agenda.servico(reserva.servico());
        Barbeiro barbeiro = Agenda.barbeiro(reserva.barbeiro());
        String acao;
        if (reserva.cancelado()) {
            acao = "<div class='mensagem neutra'>O horário foi liberado.</div><a class='botao' href='/agendar'>Escolher um novo horário <span aria-hidden='true'>↗</span></a>";
        } else if (!agenda.podeCancelar(reserva)) {
            acao = "<p class='mensagem neutra'>A data deste atendimento já passou ou o atendimento já começou.</p><a class='botao' href='/agendar'>Agendar novamente <span aria-hidden='true'>↗</span></a>";
        } else {
            String campoTelefone = telefone.isBlank()
                ? "<label for='telefone'>Confirme o telefone usado na reserva<input id='telefone' name='telefone' type='tel' inputmode='tel' autocomplete='tel-national' placeholder='(19) 99999-9999' maxlength='16' required></label>"
                : "<input type='hidden' name='telefone' value='" + escapar(telefone) + "'>";
            acao = "<details class='cancelar-reserva'><summary>Cancelar este agendamento</summary><form action='/cancelar' method='post' id='form-cancelar'><input type='hidden' name='token' value='" + token
                + "'><input type='hidden' name='codigo' value='" + reserva.codigo()
                + "'>" + campoTelefone
                + "<label class='confirmar-cancelamento'><input type='checkbox' required name='confirmacao' value='sim'> Quero cancelar este agendamento e liberar o horário.</label><button class='botao perigo' type='submit'>Cancelar agendamento</button></form></details>";
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
            + aviso(aviso) + "<a class='botao' href='/agendar'>Voltar para a agenda <span aria-hidden='true'>↗</span></a></section>";
        return layout("Confira os dados — FL Barbearia", conteudo);
    }

    private String layout(String titulo, String conteudo) throws IOException {
        return layout(titulo, conteudo, "pagina-interna");
    }

    private String layout(String titulo, String conteudo, String classe) throws IOException {
        return preencher("layout.html", Map.of("titulo", titulo, "conteudo", conteudo, "classe", classe));
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
