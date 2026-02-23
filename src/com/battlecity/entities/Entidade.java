package com.battlecity.entities;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 
 * define o contrato mínimo compartilhado por Tanques e Projéteis:
 * posição, velocidade, direção, sprites e hitbox de colisão.
 *
 * encapsulamento: campos de estado interno são protected para que
 * subclasses acessem diretamente sem overhead de getter em hot loops,
 * mas permaneçam ocultos para código externo ao pacote.
 *Hitbox: cada subclasse configura hitbox no seu construtor com as
 * dimensões e margens corretas. {@code getHitbox()} retorna sempre este campo —
 * garantindo que haja uma única hitbox por entidade, usada de forma consistente
 * tanto nas colisões de projéteis (acesso direto) quanto nas de power-ups (via getter).
 */
public abstract class Entidade {

    protected int     x, y;
    protected int     velocidade;
    protected String  direcao = "cima";
    protected boolean vivo    = true;

    // srites para as 4 direções de movimento
    protected BufferedImage up, down, left, right;

    /**
     * área de colisão da entidade.
     * public justificado pelo acesso direto em loops críticos no GamePanel
     * (verificarColisoes é chamado 60x/s e itera sobre todas as entidades)
     * ubclasses devem configurar este campo no construtor com margem adequada.
     */
    public Rectangle hitbox;
    // CONSTRUTOR

    /**
     * inicializa posição, velocidade e uma hitbox padrão de 40×40 sem margem.
     * subclasses devem sobrescrever {@code hitbox} no próprio construtor para
     * aplicar as margens corretas (ex: 36×36 com offset de 2px nos tanques).
     */
    public Entidade(int x, int y, int velocidade) {
        this.x          = x;
        this.y          = y;
        this.velocidade = velocidade;
        this.hitbox     = new Rectangle(x, y, 40, 40);
    }
    // GETTERS E SETTERS

    public int     getX()               { return x; }
    public int     getY()               { return y; }
    public void    setX(int x)          { this.x = x; }
    public void    setY(int y)          { this.y = y; }

    public int     getVelocidade()      { return velocidade; }
    public void    setVelocidade(int v) { this.velocidade = v; }

    public String  getDirecao()         { return direcao; }
    public void    setDirecao(String d) { this.direcao = d; }

    public boolean isVivo()                { return vivo; }
    public void    setVivo(boolean vivo)   { this.vivo = vivo; }

    /**
     * retorna a hitbox desta entidade — o mesmo objeto hitbox configurado
     * pela subclasse no construtor, já com as margens corretas aplicadas
     * não cria um novo Rectangle a cada chamada: tanto o acesso direto via
     * code entidade.hitbox quanto via {@code entidade.getHitbox()} retornam
     * o mesmo objeto, eliminando a inconsistência anterior onde os dois caminhos
     * produziam retângulos de tamanhos diferentes.
     */
    public Rectangle getHitbox() {
        return hitbox;
    }

    // CONTRATO DAS SUBCLASSES


    // chamado a cada frame pelo game loop para atualizar estado interno
    public abstract void atualizar();

    //chamado a cada frame pelo game loop para renderizar a entidade
    public abstract void desenhar(Graphics2D g2);
}