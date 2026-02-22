package com.battlecity.entities;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Classe base abstrata de toda entidade do jogo.
 *
 * Define o contrato mínimo compartilhado por Tanques e Projéteis:
 * posição, velocidade, direção, sprites e hitbox de colisão.
 *
 * Encapsulamento: campos são {@code protected} para que subclasses
 * acessem diretamente (sem overhead de getter em hot loops), mas
 * permaneçam ocultos para código externo ao pacote.
 */
public abstract class Entidade {

    protected int    x, y;
    protected int    velocidade;
    protected String direcao = "cima";
    protected boolean vivo   = true;

    /** Sprites para as 4 direções de movimento. */
    protected BufferedImage up, down, left, right;

    /**
     * Área de colisão da entidade.
     * {@code public} para acesso direto no GamePanel durante verificação de colisões,
     * onde getters adicionariam custo desnecessário em loop crítico.
     */
    public Rectangle hitbox;

    // =========================================================================
    // CONSTRUTOR
    // =========================================================================

    public Entidade(int x, int y, int velocidade) {
        this.x          = x;
        this.y          = y;
        this.velocidade = velocidade;
        this.hitbox     = new Rectangle(x, y, 40, 40);
    }

    // =========================================================================
    // GETTERS E SETTERS
    // =========================================================================

    public int     getX()          { return x; }
    public int     getY()          { return y; }
    public void    setX(int x)     { this.x = x; }
    public void    setY(int y)     { this.y = y; }

    public int     getVelocidade()          { return velocidade; }
    public void    setVelocidade(int v)     { this.velocidade = v; }

    public String  getDirecao()             { return direcao; }
    public void    setDirecao(String d)     { this.direcao = d; }

    public boolean isVivo()                 { return vivo; }
    public void    setVivo(boolean vivo)    { this.vivo = vivo; }

    /**
     * Retorna uma hitbox com margem de 2px em relação ao sprite (36×36 em um tile de 40×40).
     * A margem evita que o tanque trave em quinas de paredes ao roçar um canto.
     */
    public Rectangle getHitbox() {
        return new Rectangle(x + 2, y + 2, 36, 36);
    }

    // =========================================================================
    // CONTRATO DAS SUBCLASSES
    // =========================================================================

    /** Chamado a cada frame pelo game loop para atualizar estado interno. */
    public abstract void atualizar();

    /** Chamado a cada frame pelo game loop para renderizar a entidade. */
    public abstract void desenhar(Graphics2D g2);
}