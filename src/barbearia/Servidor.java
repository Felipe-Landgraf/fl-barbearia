package barbearia;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.BindException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Executors;

public class Servidor {
    private final Agenda agenda;
    private final Banco banco;
    private final Pagina pagina;
    private final Path site;
    private final String token;
    private final int porta;
    private final Configuracao configuracao;

    public Servidor(Path pasta, Path dados, Configuracao configuracao) throws IOException, SQLException {
        this.configuracao = configuracao;
        this.porta = configuracao.porta;
        site = pasta.resolve("site");
        banco = configuracao.abrirBanco(dados);
        agenda = new Agenda(banco, Clock.system(configuracao.fuso));
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        token = HexFormat.of().formatHex(bytes);
        pagina = new Pagina(site, agenda, token);
    }

    public HttpServer iniciar() throws IOException {
        String interfaceRede = configuracao.online ? "0.0.0.0" : "127.0.0.1";
        HttpServer servidor = HttpServer.create(new InetSocketAddress(interfaceRede, porta), 0);
        servidor.createContext("/", this::atender);
        servidor.setExecutor(Executors.newFixedThreadPool(6));
        servidor.start();
        return servidor;
    }

    private void atender(HttpExchange pedido) throws IOException {
        try {
            String host = pedido.getRequestHeaders().getFirst("Host");
            if (!configuracao.hostPermitido(host)) {
                responder(pedido, 403, pagina.erro("Abra o projeto pelo endereço oficial do site."));
                return;
            }
            String caminho = pedido.getRequestURI().getPath();
            String metodo = pedido.getRequestMethod();
            if (caminho.equals("/saude") && (metodo.equals("GET") || metodo.equals("HEAD"))) {
                cabecalhos(pedido);
                pedido.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                pedido.sendResponseHeaders(200, metodo.equals("HEAD") ? -1 : 2);
                if (!metodo.equals("HEAD")) pedido.getResponseBody().write("OK".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (metodo.equals("GET") && arquivo(pedido, caminho)) return;
            if (metodo.equals("GET")) {
                Map<String, String> dados = parametros(pedido.getRequestURI().getRawQuery());
                if (caminho.equals("/")) responder(pedido, 200, pagina.inicio(reservasDoNavegador(pedido)));
                else if (caminho.equals("/agendar")) responder(pedido, 200, pagina.agendar(dados, ""));
                else if (caminho.equals("/consultar")) responder(pedido, 200,
                    pagina.consulta(reservasDoNavegador(pedido), dados.getOrDefault("codigo", ""), ""));
                else if (caminho.equals("/reserva")) {
                    Agendamento reserva = banco.buscar(dados.getOrDefault("codigo", ""));
                    if (reserva == null) responder(pedido, 404, pagina.erro("Esse agendamento não foi encontrado."));
                    else responder(pedido, 200, pagina.reserva(reserva, "", dados.containsKey("novo")));
                } else responder(pedido, 404, pagina.erro("Essa página não foi encontrada."));
                return;
            }
            if (!metodo.equals("POST")) {
                pedido.getResponseHeaders().set("Allow", "GET, POST");
                responder(pedido, 405, pagina.erro("Essa ação não está disponível."));
                return;
            }
            byte[] corpo = pedido.getRequestBody().readNBytes(8193);
            if (corpo.length > 8192) {
                responder(pedido, 413, pagina.erro("O formulário ultrapassou o tamanho permitido."));
                return;
            }
            Map<String, String> dados = parametros(new String(corpo, StandardCharsets.UTF_8));
            if (!token.equals(dados.get("token")) || !configuracao.origemPermitida(pedido.getRequestHeaders().getFirst("Origin"))) {
                responder(pedido, 403, pagina.erro("Atualize a página e envie o formulário novamente."));
                return;
            }
            if (caminho.equals("/agendar")) {
                try {
                    String codigo = agenda.agendar(dados.get("servico"), dados.get("barbeiro"),
                        LocalDate.parse(dados.getOrDefault("data", "")), LocalTime.parse(dados.getOrDefault("horario", "")),
                        dados.get("nome"), dados.get("telefone"));
                    lembrarReserva(pedido, codigo);
                    redirecionar(pedido, "/reserva?codigo=" + codigo + "&novo=1");
                } catch (IllegalArgumentException erro) {
                    dados.put("etapa", "horarios");
                    responder(pedido, 400, pagina.agendar(dados, erro.getMessage()));
                }
            } else if (caminho.equals("/consultar")) {
                try {
                    Agendamento reserva = agenda.consultar(dados.get("codigo"), dados.get("telefone"));
                    lembrarReserva(pedido, reserva.codigo());
                    responder(pedido, 200, pagina.reserva(reserva, Agenda.limparTelefone(dados.get("telefone")), false));
                } catch (IllegalArgumentException erro) {
                    responder(pedido, 400, pagina.consulta(reservasDoNavegador(pedido), dados.getOrDefault("codigo", ""), erro.getMessage()));
                }
            } else if (caminho.equals("/cancelar")) {
                try {
                    if (!"sim".equals(dados.get("confirmacao"))) throw new IllegalArgumentException("Marque a confirmação antes de cancelar.");
                    agenda.cancelar(dados.get("codigo"), dados.get("telefone"));
                    String codigo = agenda.consultar(dados.get("codigo"), dados.get("telefone")).codigo();
                    redirecionar(pedido, "/reserva?codigo=" + codigo);
                } catch (IllegalArgumentException erro) {
                    responder(pedido, 400, pagina.consulta(reservasDoNavegador(pedido), dados.getOrDefault("codigo", ""), erro.getMessage()));
                }
            } else responder(pedido, 404, pagina.erro("Essa ação não foi encontrada."));
        } catch (IllegalArgumentException | java.time.DateTimeException erro) {
            responder(pedido, 400, pagina.erro("Confira os dados enviados e escolha uma data e horário válidos."));
        } catch (SQLException erro) {
            System.err.println("Não foi possível concluir a operação no banco: " + erro.getSQLState());
            responder(pedido, 500, pagina.erro("Não foi possível salvar agora. Tente novamente em instantes."));
        } finally {
            pedido.close();
        }
    }

    private List<String> codigosSalvos(HttpExchange pedido) {
        List<String> codigos = new ArrayList<>();
        String cookies = pedido.getRequestHeaders().getFirst("Cookie");
        if (cookies == null) return codigos;
        for (String cookie : cookies.split(";")) {
            String[] partes = cookie.strip().split("=", 2);
            if (partes.length != 2 || !partes[0].equals("fl_reservas")) continue;
            for (String codigo : partes[1].split("_")) {
                if (codigo.matches("[A-F0-9]{16}") && !codigos.contains(codigo) && codigos.size() < 5) codigos.add(codigo);
            }
        }
        return codigos;
    }

    private List<Agendamento> reservasDoNavegador(HttpExchange pedido) throws SQLException {
        List<Agendamento> reservas = new ArrayList<>();
        for (String codigo : codigosSalvos(pedido)) {
            Agendamento reserva = banco.buscar(codigo);
            if (reserva != null) reservas.add(reserva);
        }
        return reservas;
    }

    private void lembrarReserva(HttpExchange pedido, String codigo) {
        List<String> codigos = codigosSalvos(pedido);
        codigos.remove(codigo);
        codigos.addFirst(codigo);
        if (codigos.size() > 5) codigos.removeLast();
        String cookie = "fl_reservas=" + String.join("_", codigos) + "; Path=/; Max-Age=5184000; HttpOnly; SameSite=Lax";
        if (configuracao.enderecoPublico.startsWith("https://")) cookie += "; Secure";
        pedido.getResponseHeaders().add("Set-Cookie", cookie);
    }

    private boolean arquivo(HttpExchange pedido, String caminho) throws IOException {
        Map<String, String> arquivos = Map.of(
            "/estilo.css", "text/css; charset=utf-8", "/agenda.css", "text/css; charset=utf-8", "/script.js", "text/javascript; charset=utf-8",
            "/imagens/simbolo.png", "image/png", "/imagens/cadeira.jpg", "image/jpeg");
        if (!arquivos.containsKey(caminho)) return false;
        byte[] conteudo = Files.readAllBytes(site.resolve(caminho.substring(1)));
        cabecalhos(pedido);
        pedido.getResponseHeaders().set("Content-Type", arquivos.get(caminho));
        pedido.sendResponseHeaders(200, conteudo.length);
        pedido.getResponseBody().write(conteudo);
        return true;
    }

    private static Map<String, String> parametros(String texto) {
        Map<String, String> dados = new HashMap<>();
        if (texto == null || texto.isBlank()) return dados;
        for (String campo : texto.split("&")) {
            String[] partes = campo.split("=", 2);
            String nome = URLDecoder.decode(partes[0], StandardCharsets.UTF_8);
            String valor = partes.length == 2 ? URLDecoder.decode(partes[1], StandardCharsets.UTF_8) : "";
            dados.put(nome, valor);
        }
        return dados;
    }

    private static void cabecalhos(HttpExchange pedido) {
        pedido.getResponseHeaders().set("X-FL-Barbearia", "1");
        pedido.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        pedido.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        pedido.getResponseHeaders().set("Cache-Control", "no-store");
        pedido.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
    }

    private static void responder(HttpExchange pedido, int status, String html) throws IOException {
        byte[] conteudo = html.getBytes(StandardCharsets.UTF_8);
        cabecalhos(pedido);
        pedido.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        pedido.sendResponseHeaders(status, conteudo.length);
        pedido.getResponseBody().write(conteudo);
    }

    private static void redirecionar(HttpExchange pedido, String endereco) throws IOException {
        cabecalhos(pedido);
        pedido.getResponseHeaders().set("Location", endereco);
        pedido.sendResponseHeaders(303, -1);
    }

    public static void main(String[] argumentos) throws Exception {
        Path pasta = Path.of(System.getProperty("barbearia.pasta", ".")).toAbsolutePath().normalize();
        Path dados = Path.of(System.getProperty("barbearia.dados", pasta.resolve("dados").toString()));
        Configuracao configuracao = new Configuracao(System.getenv());
        int porta = configuracao.porta;
        Servidor aplicacao = new Servidor(pasta, dados, configuracao);
        String endereco = configuracao.enderecoPublico;
        try {
            HttpServer servidor = aplicacao.iniciar();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> servidor.stop(0)));
        } catch (BindException erro) {
            if (configuracao.online) throw erro;
            HttpURLConnection resposta = (HttpURLConnection) URI.create(endereco).toURL().openConnection();
            resposta.setConnectTimeout(1500);
            resposta.setReadTimeout(1500);
            try {
                if (!"1".equals(resposta.getHeaderField("X-FL-Barbearia"))) throw erro;
            } finally {
                resposta.disconnect();
            }
            System.out.println("A FL Barbearia já está aberta em " + endereco);
            abrirNavegador(endereco);
            return;
        }
        System.out.println("FL Barbearia está aberta em " + endereco);
        System.out.println("Mantenha esta janela aberta enquanto estiver usando o site.");
        System.out.println("Para encerrar, feche esta janela ou pressione Ctrl+C.");
        if (!configuracao.online) abrirNavegador(endereco);
    }

    private static void abrirNavegador(String endereco) {
        if (!Boolean.getBoolean("barbearia.semNavegador") && Desktop.isDesktopSupported()) {
            try { Desktop.getDesktop().browse(URI.create(endereco)); }
            catch (IOException | UnsupportedOperationException erro) { System.out.println("Abra o endereço acima no navegador."); }
        }
    }
}
