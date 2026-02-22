package com.battlecity.entities;

import com.battlecity.main.GamePanel;
import com.battlecity.main.ManipuladorTeclas;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Tanque controlado pelo jogador via teclado. Roda em thread própria.
 *
 * Sistema de sobrevivência:
 *   - {@code vidas}: estoque de tanques reserva (continuar após morte)
 *   - {@code hp}:    energia do tanque atual (0–100, cada tiro causa 25 de dano)
 *   - Invencibilidade: 3 segundos após renascer, evita morte instantânea no respawn
 *
 * Por que thread própria?
 * O professor exige uma thread por tanque. Isso separa a lógica de input
 * e movimento da renderização do game loop principal.
 */
public class TanqueJogador extends Entidade implements Movivel, Runnable {

    private final GamePanel         gp;
    private final ManipuladorTeclas teclaH;
    private       Thread            threadTanque;
    private       int               contadorTiro = 0;

    // =========================================================================
    // ESTADO DE SOBREVIVÊNCIA
    // =========================================================================

    public int  vidas;
    public int  hp;
    public long tempoNascimento;

    private static final long DURACAO_INVENCIBILIDADE_MS = 3_000;

    // =========================================================================
    // CONSTRUTOR
    // =========================================================================

    public TanqueJogador(GamePanel gp, ManipuladorTeclas teclaH) {
        super(40, 440, 4); // Posição inicial: canto inferior esquerdo
        this.gp     = gp;
        this.teclaH = teclaH;

        this.vidas = 5;
        this.hp    = gp.HP_MAXIMO;
        this.vivo  = true;

        this.hitbox.width  = gp.HITBOX_TAMANHO;
        this.hitbox.height = gp.HITBOX_TAMANHO;

        this.tempoNascimento = System.currentTimeMillis();

        carregarSprites();

        threadTanque = new Thread(this);
        threadTanque.start();
    }

    // =========================================================================
    // ESTADO DE SOBREVIVÊNCIA
    // =========================================================================

    /** Retorna true se o jogador ainda está no período de invencibilidade pós-respawn. */
    public boolean isInvencivel() {
        return (System.currentTimeMillis() - tempoNascimento) < DURACAO_INVENCIBILIDADE_MS;
    }

    public void restaurarHP() {
        this.hp = gp.HP_MAXIMO;
    }

    /**
     * Reposiciona o jogador, restaura HP e inicia o período de invencibilidade.
     * Chamado ao renascer após uma morte.
     */
    public void posicionarEmSeguranca(int novoX, int novoY) {
        this.x = novoX;
        this.y = novoY;
        restaurarHP();
        this.tempoNascimento = System.currentTimeMillis();

        if (this.hitbox != null) {
            this.hitbox.x = novoX + gp.HITBOX_MARGEM;
            this.hitbox.y = novoY + gp.HITBOX_MARGEM;
        }
    }

    // =========================================================================
    // THREAD — LOOP DE MOVIMENTO
    // =========================================================================

    @Override
    public void run() {
        while (threadTanque != null) {
            // Só processa input durante o jogo — evita mover/atirar durante MORTE e GAMEOVER
            if (gp.estadoJogo == gp.ESTADO_JOGANDO) {
                mover();
            }
            try {
                Thread.sleep(gp.THREAD_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Movimento delegado à thread — existe apenas para cumprir o contrato de Entidade. */
    @Override
    public void atualizar() {}

    // =========================================================================
    // LÓGICA DE MOVIMENTO E TIRO
    // =========================================================================

    @Override
    public void mover() {
        if (vidas <= 0 && hp <= 0) return;

        int     proximoX  = x;
        int     proximoY  = y;
        boolean podeMover = true;

        final int MARGEM       = 3;
        final int LARGURA_UTIL = gp.TAMANHO_BLOCO - MARGEM;

        if      (teclaH.cima)     { direcao = "cima";     proximoY -= velocidade; }
        else if (teclaH.baixo)    { direcao = "baixo";    proximoY += velocidade; }
        else if (teclaH.esquerda) { direcao = "esquerda"; proximoX -= velocidade; }
        else if (teclaH.direita)  { direcao = "direita";  proximoX += velocidade; }

        Rectangle hitboxProxima = new Rectangle(
            proximoX + gp.HITBOX_MARGEM, proximoY + gp.HITBOX_MARGEM,
            gp.HITBOX_TAMANHO, gp.HITBOX_TAMANHO
        );

        // Look-ahead: verifica os dois cantos da frente para evitar travamento em quinas
        switch (direcao) {
            case "cima" -> {
                if (gp.gerenciadorMapa.verificarPassagem(x + MARGEM, proximoY) ||
                    gp.gerenciadorMapa.verificarPassagem(x + LARGURA_UTIL, proximoY))
                    podeMover = false;
            }
            case "baixo" -> {
                if (gp.gerenciadorMapa.verificarPassagem(x + MARGEM, proximoY + gp.TAMANHO_BLOCO - 1) ||
                    gp.gerenciadorMapa.verificarPassagem(x + LARGURA_UTIL, proximoY + gp.TAMANHO_BLOCO - 1))
                    podeMover = false;
            }
            case "esquerda" -> {
                if (gp.gerenciadorMapa.verificarPassagem(proximoX, y + MARGEM) ||
                    gp.gerenciadorMapa.verificarPassagem(proximoX, y + LARGURA_UTIL))
                    podeMover = false;
            }
            case "direita" -> {
                if (gp.gerenciadorMapa.verificarPassagem(proximoX + gp.TAMANHO_BLOCO - 1, y + MARGEM) ||
                    gp.gerenciadorMapa.verificarPassagem(proximoX + gp.TAMANHO_BLOCO - 1, y + LARGURA_UTIL))
                    podeMover = false;
            }
        }

        if (colideComInimigos(hitboxProxima)) podeMover = false;
        if (proximoX < 0 || proximoY < 0 ||
            proximoX > gp.LARGURA_TELA - gp.TAMANHO_BLOCO ||
            proximoY > gp.ALTURA_TELA  - gp.TAMANHO_BLOCO) podeMover = false;

        if (podeMover) {
            x        = proximoX;
            y        = proximoY;
            hitbox.x = x + gp.HITBOX_MARGEM;
            hitbox.y = y + gp.HITBOX_MARGEM;
        }

        if (teclaH.tiro && contadorTiro >= 30) efetuarDisparo();
        if (contadorTiro < 30) contadorTiro++;
    }

    /**
     * Cria um projétil na frente do tanque na direção atual.
     * {@code synchronized}: captura atômica de x e y para evitar race condition
     * entre a thread do jogador e a leitura das coordenadas.
     */
    private synchronized void efetuarDisparo() {
        int snapshotX = x;
        int snapshotY = y;
        int centroX   = snapshotX + 16;
        int centroY   = snapshotY + 16;
        int balaX     = snapshotX;
        int balaY     = snapshotY;

        switch (direcao) {
            case "cima"     -> { balaX = centroX;        balaY = snapshotY - 10; }
            case "baixo"    -> { balaX = centroX;        balaY = snapshotY + 40; }
            case "esquerda" -> { balaX = snapshotX - 10; balaY = centroY;        }
            case "direita"  -> { balaX = snapshotX + 40; balaY = centroY;        }
        }

        gp.adicionarProjetil(new Projetil(gp, balaX, balaY, direcao, this));
        contadorTiro = 0;
    }

    private boolean colideComInimigos(Rectangle hitboxProxima) {
        for (TanqueInimigo inimigo : gp.listaInimigos) {
            if (inimigo.isVivo() && hitboxProxima.intersects(inimigo.hitbox)) return true;
        }
        return false;
    }

    // =========================================================================
    // RENDERIZAÇÃO
    // =========================================================================

    @Override
    public void desenhar(Graphics2D g2) {
        if (!vivo && hp <= 0) return;

        // Pisca a cada 100ms durante a invencibilidade — feedback visual ao jogador
        if (isInvencivel() && (System.currentTimeMillis() / 100) % 2 == 0) return;

        BufferedImage sprite = switch (direcao) {
            case "cima"     -> up;
            case "baixo"    -> down;
            case "esquerda" -> left;
            case "direita"  -> right;
            default         -> up;
        };

        if (sprite != null) {
            g2.drawImage(sprite, x, y, gp.TAMANHO_BLOCO, gp.TAMANHO_BLOCO, null);
        }
    }

    // =========================================================================
    // SISTEMA DE ASSETS
    // =========================================================================

    /** Carrega os 4 sprites direcionais removendo o fundo branco dos BMPs. */
    public void carregarSprites() {
        try {
            up    = ImageUtils.transformarTransparente(
                        ImageIO.read(getClass().getResourceAsStream("/imagens/playerUp.bmp")),    Color.WHITE);
            down  = ImageUtils.transformarTransparente(
                        ImageIO.read(getClass().getResourceAsStream("/imagens/playerDown.bmp")),  Color.WHITE);
            left  = ImageUtils.transformarTransparente(
                        ImageIO.read(getClass().getResourceAsStream("/imagens/playerLeft.bmp")),  Color.WHITE);
            right = ImageUtils.transformarTransparente(
                        ImageIO.read(getClass().getResourceAsStream("/imagens/playerRight.bmp")), Color.WHITE);
        } catch (Exception e) {
            System.err.println("Erro ao carregar sprites do jogador: " + e.getMessage());
        }
    }
}