package barbearia;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Banco {
    private final String endereco;
    private final Properties propriedades;

    public Banco(String endereco) throws SQLException {
        this(endereco, acessoLocal());
    }

    public Banco(String endereco, Properties propriedades) throws SQLException {
        this.endereco = endereco;
        this.propriedades = new Properties();
        this.propriedades.putAll(propriedades);
        try (Connection conexao = conectar(); Statement comando = conexao.createStatement()) {
            comando.execute("""
                CREATE TABLE IF NOT EXISTS agendamentos (
                    codigo VARCHAR(16) PRIMARY KEY,
                    servico VARCHAR(30) NOT NULL,
                    barbeiro VARCHAR(30) NOT NULL,
                    dia DATE NOT NULL,
                    horario TIME NOT NULL,
                    nome VARCHAR(80) NOT NULL,
                    telefone VARCHAR(11) NOT NULL,
                    cancelado BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
            comando.execute("""
                CREATE TABLE IF NOT EXISTS horarios_ocupados (
                    barbeiro VARCHAR(30) NOT NULL,
                    dia DATE NOT NULL,
                    horario TIME NOT NULL,
                    codigo VARCHAR(16) NOT NULL REFERENCES agendamentos(codigo),
                    PRIMARY KEY (barbeiro, dia, horario)
                )
                """);
        }
    }

    private Connection conectar() throws SQLException {
        return DriverManager.getConnection(endereco, propriedades);
    }

    private static Properties acessoLocal() {
        Properties propriedades = new Properties();
        propriedades.setProperty("user", "sa");
        propriedades.setProperty("password", "");
        return propriedades;
    }

    public List<LocalTime> horariosOcupados(String barbeiro, LocalDate dia) throws SQLException {
        List<LocalTime> horarios = new ArrayList<>();
        try (Connection conexao = conectar(); PreparedStatement comando = conexao.prepareStatement(
            "SELECT horario FROM horarios_ocupados WHERE barbeiro = ? AND dia = ?")) {
            comando.setString(1, barbeiro);
            comando.setObject(2, dia);
            try (ResultSet linhas = comando.executeQuery()) {
                while (linhas.next()) horarios.add(linhas.getObject("horario", LocalTime.class));
            }
        }
        return horarios;
    }

    public void salvar(Agendamento agendamento, int minutos) throws SQLException {
        try (Connection conexao = conectar()) {
            conexao.setAutoCommit(false);
            try (PreparedStatement comando = conexao.prepareStatement(
                "INSERT INTO agendamentos (codigo, servico, barbeiro, dia, horario, nome, telefone) VALUES (?, ?, ?, ?, ?, ?, ?)");
                 PreparedStatement ocupar = conexao.prepareStatement(
                "INSERT INTO horarios_ocupados (barbeiro, dia, horario, codigo) VALUES (?, ?, ?, ?)")) {
                comando.setString(1, agendamento.codigo());
                comando.setString(2, agendamento.servico());
                comando.setString(3, agendamento.barbeiro());
                comando.setObject(4, agendamento.dia());
                comando.setObject(5, agendamento.horario());
                comando.setString(6, agendamento.nome());
                comando.setString(7, agendamento.telefone());
                comando.executeUpdate();
                for (int minuto = 0; minuto < minutos; minuto += 15) {
                    ocupar.setString(1, agendamento.barbeiro());
                    ocupar.setObject(2, agendamento.dia());
                    ocupar.setObject(3, agendamento.horario().plusMinutes(minuto));
                    ocupar.setString(4, agendamento.codigo());
                    ocupar.executeUpdate();
                }
                conexao.commit();
            } catch (SQLException erro) {
                conexao.rollback();
                if ("23505".equals(erro.getSQLState())) {
                    throw new IllegalArgumentException("Esse horário acabou de ser reservado. Escolha outro na agenda.");
                }
                throw erro;
            }
        }
    }

    public Agendamento buscar(String codigo) throws SQLException {
        try (Connection conexao = conectar(); PreparedStatement comando = conexao.prepareStatement(
            "SELECT * FROM agendamentos WHERE codigo = ?")) {
            comando.setString(1, codigo);
            try (ResultSet linha = comando.executeQuery()) {
                if (!linha.next()) return null;
                return new Agendamento(linha.getString("codigo"), linha.getString("servico"),
                    linha.getString("barbeiro"), linha.getObject("dia", LocalDate.class),
                    linha.getObject("horario", LocalTime.class), linha.getString("nome"),
                    linha.getString("telefone"), linha.getBoolean("cancelado"));
            }
        }
    }

    public void cancelar(String codigo) throws SQLException {
        try (Connection conexao = conectar()) {
            conexao.setAutoCommit(false);
            try (PreparedStatement atualizar = conexao.prepareStatement("UPDATE agendamentos SET cancelado = TRUE WHERE codigo = ?");
                 PreparedStatement liberar = conexao.prepareStatement("DELETE FROM horarios_ocupados WHERE codigo = ?")) {
                atualizar.setString(1, codigo);
                atualizar.executeUpdate();
                liberar.setString(1, codigo);
                liberar.executeUpdate();
                conexao.commit();
            } catch (SQLException erro) {
                conexao.rollback();
                throw erro;
            }
        }
    }
}
