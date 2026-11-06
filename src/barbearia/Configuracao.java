package barbearia;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.Map;
import java.util.Properties;

public class Configuracao {
    private final Map<String, String> ambiente;
    public final int porta;
    public final boolean online;
    public final String enderecoPublico;
    public final ZoneId fuso;

    public Configuracao(Map<String, String> ambiente) {
        this.ambiente = Map.copyOf(ambiente);
        porta = Integer.parseInt(System.getProperty("barbearia.porta", ambiente.getOrDefault("PORT", "8085")));
        if (porta < 1 || porta > 65535) throw new IllegalArgumentException("A porta do site é inválida.");
        online = "true".equals(ambiente.get("RENDER")) || ambiente.containsKey("PORT")
            || !ambiente.getOrDefault("APP_URL", "").isBlank();
        String definido = ambiente.getOrDefault("APP_URL", "");
        enderecoPublico = (definido.isBlank() ? ambiente.getOrDefault("RENDER_EXTERNAL_URL", "http://127.0.0.1:" + porta) : definido).replaceAll("/+$", "");
        URI endereco = URI.create(enderecoPublico);
        if (endereco.getHost() == null || endereco.getUserInfo() != null
            || !("http".equals(endereco.getScheme()) || "https".equals(endereco.getScheme()))) {
            throw new IllegalArgumentException("Informe um endereço válido para o site.");
        }
        if ("true".equals(ambiente.get("RENDER")) && ambiente.getOrDefault("DATABASE_URL", "").isBlank()) {
            throw new IllegalArgumentException("Configure DATABASE_URL antes de publicar no Render.");
        }
        fuso = ZoneId.of(ambiente.getOrDefault("TZ", "America/Sao_Paulo"));
    }

    public Banco abrirBanco(Path dados) throws IOException, SQLException {
        String endereco = ambiente.getOrDefault("DATABASE_URL", "");
        if (endereco.isBlank()) {
            Files.createDirectories(dados);
            return new Banco("jdbc:h2:file:" + dados.resolve("agenda").toAbsolutePath().toString().replace('\\', '/'));
        }
        return bancoPostgres(endereco);
    }

    public static Banco bancoPostgres(String endereco) throws SQLException {
        URI url = URI.create(endereco);
        if (!("postgres".equals(url.getScheme()) || "postgresql".equals(url.getScheme()))
            || url.getHost() == null || url.getRawUserInfo() == null || url.getRawPath() == null
            || url.getRawPath().length() < 2) {
            throw new IllegalArgumentException("DATABASE_URL precisa ser um endereço PostgreSQL válido.");
        }
        String[] acesso = url.getRawUserInfo().split(":", 2);
        if (acesso.length != 2) throw new IllegalArgumentException("Informe usuário e senha na conexão do banco.");
        Properties propriedades = new Properties();
        propriedades.setProperty("user", URLDecoder.decode(acesso[0].replace("+", "%2B"), StandardCharsets.UTF_8));
        propriedades.setProperty("password", URLDecoder.decode(acesso[1].replace("+", "%2B"), StandardCharsets.UTF_8));
        propriedades.setProperty("connectTimeout", "15");
        propriedades.setProperty("socketTimeout", "30");
        propriedades.setProperty("tcpKeepAlive", "true");
        boolean local = "localhost".equals(url.getHost()) || "127.0.0.1".equals(url.getHost());
        propriedades.setProperty("sslmode", local ? "disable" : "verify-full");
        int porta = url.getPort() == -1 ? 5432 : url.getPort();
        String jdbc = "jdbc:postgresql://" + url.getHost() + ":" + porta + url.getRawPath();
        return new Banco(jdbc, propriedades);
    }

    public boolean hostPermitido(String host) {
        if (host == null) return false;
        if (host.equalsIgnoreCase("127.0.0.1:" + porta) || host.equalsIgnoreCase("localhost:" + porta)) return true;
        if (!online) return false;
        URI publico = URI.create(enderecoPublico);
        return host.equalsIgnoreCase(publico.getRawAuthority())
            || host.equalsIgnoreCase(publico.getHost() + ":443");
    }

    public boolean origemPermitida(String origem) {
        return origem == null || origem.equalsIgnoreCase(enderecoPublico)
            || (!online && (origem.equalsIgnoreCase("http://127.0.0.1:" + porta)
                || origem.equalsIgnoreCase("http://localhost:" + porta)));
    }
}
