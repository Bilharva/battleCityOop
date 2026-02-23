package com.battlecity.main;

import javax.swing.*;
import java.awt.*;

/**
 * tela de alistamento: jogador informa nome e escolhe a dificuldade antes de iniciar.
 * valida que o nome não está vazio antes de permitir o início da partida.
 */
public class TelaAlistamento extends JPanel {

    private final Main              main;
    private final JTextField        campoNome;
    private final JComboBox<String> comboDificuldade;

    public TelaAlistamento(Main main) {
        this.main = main;

        this.setPreferredSize(new Dimension(720, 520));
        this.setBackground(Color.BLACK);
        this.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);
        gbc.gridx  = 0;

        gbc.gridy = 0;
        JLabel titulo = new JLabel("BATTLE CITY - ALISTAMENTO");
        titulo.setForeground(Color.GREEN);
        titulo.setFont(new Font("Monospaced", Font.BOLD, 36));
        this.add(titulo, gbc);

        gbc.gridy = 1;
        JLabel lblInstrucao = new JLabel("DIGITE SEU NOME DE GUERRA:");
        lblInstrucao.setForeground(Color.WHITE);
        lblInstrucao.setFont(new Font("Monospaced", Font.BOLD, 20));
        this.add(lblInstrucao, gbc);

        gbc.gridy = 2;
        campoNome = new JTextField("", 15);
        campoNome.setFont(new Font("Monospaced", Font.PLAIN, 22));
        campoNome.setBackground(Color.BLACK);
        campoNome.setForeground(Color.YELLOW);
        campoNome.setCaretColor(Color.GREEN);
        campoNome.setHorizontalAlignment(JTextField.CENTER);
        campoNome.setBorder(BorderFactory.createLineBorder(Color.GREEN, 2));
        this.add(campoNome, gbc);

        gbc.gridy = 3;
        JLabel lblDificuldade = new JLabel("SELECIONE A DIFICULDADE:");
        lblDificuldade.setForeground(Color.WHITE);
        lblDificuldade.setFont(new Font("Monospaced", Font.BOLD, 20));
        this.add(lblDificuldade, gbc);

        gbc.gridy = 4;
        comboDificuldade = new JComboBox<>(new String[]{"FÁCIL", "MÉDIO", "DIFÍCIL"});
        comboDificuldade.setFont(new Font("Monospaced", Font.BOLD, 20));
        comboDificuldade.setBackground(Color.BLACK);
        comboDificuldade.setForeground(Color.YELLOW);
        comboDificuldade.setPreferredSize(new Dimension(300, 40));
        this.add(comboDificuldade, gbc);

        gbc.gridy = 5;
        JButton btnIniciar = new JButton("INICIAR GAME");
        btnIniciar.setBackground(new Color(30, 80, 30));
        btnIniciar.setForeground(Color.WHITE);
        btnIniciar.setFont(new Font("Monospaced", Font.BOLD, 22));
        btnIniciar.setFocusPainted(false);
        btnIniciar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnIniciar.addActionListener(e -> tentarIniciarJogo());
        this.add(btnIniciar, gbc);
    }

    /**
     * valida o nome e inicia o jogo, ou exibe alerta se o campo estiver vazio
     * getSelectedIndex() retorna 0=Fácil, 1=Médio, 2=Difícil — mapeado diretamente no GamePanel
     */
    private void tentarIniciarJogo() {
        String nome = campoNome.getText().trim().toUpperCase();

        if (nome.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "SOLDADO! Identifique-se antes de prosseguir!",
                "!!!!!!!!",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        int dificuldade = comboDificuldade.getSelectedIndex();
        System.out.println("Soldado " + nome + " pronto para o combate. Dificuldade: " + dificuldade);
        main.iniciarJogo(nome, dificuldade);
    }
}