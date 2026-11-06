package barbearia;

import java.util.Map;

public class TesteConfiguracao {
    private static int aprovados;

    public static void main(String[] argumentos) {
        Configuracao local = new Configuracao(Map.of());
        conferir("Modo local mantém o acesso restrito ao computador", !local.online && local.hostPermitido("127.0.0.1:8085") && !local.hostPermitido("outro-site.com"));
        conferir("Horários usam o fuso de São Paulo", local.fuso.getId().equals("America/Sao_Paulo"));
        Configuracao online = new Configuracao(Map.of("RENDER", "true", "PORT", "10000",
            "RENDER_EXTERNAL_URL", "https://fl-teste.onrender.com", "DATABASE_URL", "postgresql://teste:teste@localhost/fl_barbearia_teste"));
        conferir("Render usa a porta e o domínio da hospedagem", online.online && online.porta == 10000 && online.enderecoPublico.equals("https://fl-teste.onrender.com"));
        conferir("Domínio oficial e verificação local são aceitos", online.hostPermitido("fl-teste.onrender.com") && online.hostPermitido("127.0.0.1:10000"));
        conferir("Domínios diferentes são recusados", !online.hostPermitido("outro-site.com") && !online.hostPermitido("outro@fl-teste.onrender.com") && !online.hostPermitido("fl-teste.onrender.com.outro-site.com"));
        conferir("Envio de formulários de outra origem é recusado", online.origemPermitida("https://fl-teste.onrender.com") && !online.origemPermitida("https://outro-site.com"));
        try {
            new Configuracao(Map.of("RENDER", "true"));
            throw new AssertionError("Publicação sem banco foi aceita.");
        } catch (IllegalArgumentException erro) {
            conferir("Render não substitui um banco ausente por arquivo temporário", erro.getMessage().contains("DATABASE_URL"));
        }
        try {
            Configuracao.bancoPostgres("https://localhost/banco");
            throw new AssertionError("Conexão inválida foi aceita.");
        } catch (IllegalArgumentException erro) {
            conferir("Endereço de banco inválido é recusado", true);
        } catch (Exception erro) {
            throw new AssertionError(erro);
        }
        System.out.println(aprovados + " testes de configuração aprovados.");
    }

    private static void conferir(String nome, boolean condicao) {
        if (!condicao) throw new AssertionError(nome);
        aprovados++;
        System.out.println("OK · " + nome);
    }
}
