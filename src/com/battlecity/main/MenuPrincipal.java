package com.battlecity.main;

import javax.swing.*;
import java.awt.*;

/**
 * Tela inicial do jogo com navegação para Play, Ranking e Sair.
 * Layout gerenciado por GridBagLayout para centralização responsiva.
 */
public class MenuPrincipal extends JPanel {

    private final Main main;

    public MenuPrincipal(Main main) {
        this.main = main;

        this.setPreferredSize(new Dimension(720, 520));
        this.setBackground(Color.BLACK);
        this.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);
        gbc.gridx  = 0;

        gbc.gridy = 0;
        JLabel titulo = new JLabel("BATTLE CITY POO");
        titulo.setFont(new Font("Monospaced", Font.BOLD, 50));
        titulo.setForeground(Color.ORANGE);
        this.add(titulo, gbc);

        gbc.gridy = 1;
        JButton btnJogar = new JButton("PLAY");
        estilizarBotao(btnJogar);
        btnJogar.addActionListener(e -> main.irParaAlistamento());
        this.add(btnJogar, gbc);

        gbc.gridy = 2;
        JButton btnRanking = new JButton("RANKING");
        estilizarBotao(btnRanking);
        btnRanking.addActionListener(e -> main.irParaRanking());
        this.add(btnRanking, gbc);

        gbc.gridy = 3;
        JButton btnSair = new JButton("SAIR");
        estilizarBotao(btnSair);
        btnSair.addActionListener(e -> System.exit(0));
        this.add(btnSair, gbc);
    }

    /**
     * Aplica estilo visual arcade consistente e efeito de hover nos botões do menu.
     * Centralizar aqui garante identidade visual uniforme sem repetição.
     */
    private void estilizarBotao(JButton botao) {
        botao.setFont(new Font("Monospaced", Font.BOLD, 22));
        botao.setBackground(new Color(40, 40, 40));
        botao.setForeground(Color.GREEN);
        botao.setFocusPainted(false);
        botao.setBorder(BorderFactory.createLineBorder(Color.GREEN, 2));
        botao.setPreferredSize(new Dimension(300, 60));
        botao.setCursor(new Cursor(Cursor.HAND_CURSOR));

        botao.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                botao.setBackground(new Color(60, 60, 60));
                botao.setForeground(Color.YELLOW);
            }
            public void mouseExited(java.awt.event.MouseEvent e) {
                botao.setBackground(new Color(40, 40, 40));
                botao.setForeground(Color.GREEN);
            }
        });
    }
}