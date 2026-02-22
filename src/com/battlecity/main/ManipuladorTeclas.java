package com.battlecity.main;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * Gerenciador de input via teclado.
 *
 * Transforma eventos assíncronos do hardware em estados booleanos estáveis
 * que o game loop pode ler a qualquer momento sem risco de perder eventos.
 *
 * Por que estados booleanos e não eventos diretos?
 * Eventos são pontuais — se o game loop estiver ocupado quando a tecla é
 * pressionada, ele perde o evento. Estados persistem até keyReleased(),
 * garantindo que nenhum input seja ignorado.
 */
public class ManipuladorTeclas implements KeyListener {

    public boolean cima, baixo, esquerda, direita, tiro, esc, enter;

    /**
     * Zera todos os estados de tecla.
     * Deve ser chamado ao trocar de estado do jogo (ex: jogo → pausa)
     * para evitar que comandos "travados" vazem entre contextos.
     */
    public void limparTeclas() {
        cima     = false;
        baixo    = false;
        esquerda = false;
        direita  = false;
        tiro     = false;
        esc      = false;
        enter    = false;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        mapearTecla(e.getKeyCode(), true);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        mapearTecla(e.getKeyCode(), false);
    }

    /** Não utilizado — keyTyped lida com caracteres Unicode, inútil para controle de movimento. */
    @Override
    public void keyTyped(KeyEvent e) {}

    /**
     * Centraliza o mapeamento de códigos de tecla para estados booleanos.
     * Suporta WASD e setas para movimento, garantindo acessibilidade a ambos os esquemas.
     */
    private void mapearTecla(int codigo, boolean pressionado) {
        if (codigo == KeyEvent.VK_W || codigo == KeyEvent.VK_UP)    cima      = pressionado;
        if (codigo == KeyEvent.VK_S || codigo == KeyEvent.VK_DOWN)  baixo     = pressionado;
        if (codigo == KeyEvent.VK_A || codigo == KeyEvent.VK_LEFT)  esquerda  = pressionado;
        if (codigo == KeyEvent.VK_D || codigo == KeyEvent.VK_RIGHT) direita   = pressionado;
        if (codigo == KeyEvent.VK_SPACE)                            tiro      = pressionado;
        if (codigo == KeyEvent.VK_ESCAPE)                           esc       = pressionado;
        if (codigo == KeyEvent.VK_ENTER)                            enter     = pressionado;
    }
}