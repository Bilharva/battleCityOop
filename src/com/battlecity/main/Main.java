package com.battlecity.main;

import javax.swing.JFrame;

/**
 * Ponto de entrada da aplicação e orquestrador de navegação entre telas.
 *
 * Responsabilidade única: controlar qual tela está ativa na janela principal.
 * Cada transição remove o conteúdo atual e adiciona o novo componente,
 * delegando toda lógica de jogo e UI para as classes de cada tela.
 */
public class Main {

    public static JFrame window;

    public static void main(String[] args) {
        window = new JFrame();
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);
        window.setTitle("Battle City - Java Edition");

        Main orquestrador = new Main();
        orquestrador.irParaMenuPrincipal();

        window.setVisible(true);
    }

    public void irParaMenuPrincipal() {
        trocarTela(new MenuPrincipal(this));
    }

    public void irParaAlistamento() {
        trocarTela(new TelaAlistamento(this));
    }

    public void irParaRanking() {
        trocarTela(new TelaRanking(this));
    }

    /**
     * Instancia o motor de jogo e inicia o game loop.
     *
     * @param nomeJogador Nome digitado na tela de alistamento.
     * @param dificuldade 0 = Fácil, 1 = Médio, 2 = Difícil.
     */
    public void iniciarJogo(String nomeJogador, int dificuldade) {
        GamePanel gamePanel = new GamePanel(this, nomeJogador, dificuldade);
        trocarTela(gamePanel);

        // iniciarThreadJogo() chamado após trocarTela() para garantir que
        // o painel já está montado na janela antes de o game loop começar.
        gamePanel.iniciarThreadJogo();
        gamePanel.requestFocusInWindow();
    }

    /**
     * Substitui o conteúdo da janela pelo novo componente e força redesenho.
     * Centralizar aqui evita repetição do pack/revalidate em cada transição.
     */
    private void trocarTela(java.awt.Component novaTela) {
        window.getContentPane().removeAll();
        window.add(novaTela);
        window.pack();
        window.setLocationRelativeTo(null);
        window.revalidate();
        window.repaint();
    }
}