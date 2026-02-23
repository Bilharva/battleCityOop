package com.battlecity.entities;

import com.battlecity.main.GamePanel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.InputStream;
import java.util.Random;

/**
 * Tanque controlado por IA. Cada instância roda em thread própria.
 *
 * Tipos disponíveis:
 *   0 = Normal  (cinza)    — velocidade 2, delay de tiro 240 ticks
 *   1 = Rápido  (verde)    — velocidade 4, delay de tiro 180 ticks
 *   2 = Pesado  (vermelho) — velocidade 1, delay de tiro 300 ticks
 *
 * Por que thread própria?
 * O professor exige uma thread por tanque. Isso permite que cada inimigo
 * se mova de forma autônoma, independente do game loop e dos outros tanques.
 */
public class TanqueInimigo extends Entidade implements Movivel, Runnable {

    private final GamePanel gp;
    private final int       tipoInimigo;
    private final Random    random            = new Random();
    private       Thread    threadInimigo;
    private       int       contadorMovimento = 0;
    private       int       contadorTiro      = 0;

    // =========================================================================
    // CONSTRUTOR
    // =========================================================================

    public TanqueInimigo(GamePanel gp, int x, int y, int tipo) {
        super(x, y, 2);
        this.gp          = gp;
        this.tipoInimigo = tipo;
        this.direcao     = "baixo";

        this.velocidade = switch (tipo) {
            case 1  -> 4; // Rápido
            case 2  -> 1; // Pesado
            default -> 2; // Normal
        };

        carregarImagens();
        this.hitbox.setBounds(x + gp.HITBOX_MARGEM, y + gp.HITBOX_MARGEM,
                              gp.HITBOX_TAMANHO, gp.HITBOX_TAMANHO);

        // Thread criada mas NÃO iniciada aqui — GamePanel.iniciarFase() chama
        // iniciarThread() após todos os inimigos serem posicionados, evitando
        // que threads comecem a mover inimigos antes do spawn estar completo.
        threadInimigo = new Thread(this);
        threadInimigo.setDaemon(true);
    }

    /** Inicia o movimento do tanque. Deve ser chamado após todos os spawns da fase. */
    public void iniciarThread() {
        if (!threadInimigo.isAlive()) {
            threadInimigo.start();
        }
    }

    // =========================================================================
    // SISTEMA DE ASSETS
    // =========================================================================

    private void carregarImagens() {
        // Sprites do tipo Normal — usados também como fallback para tipos sem asset
        String spriteCima     = "/imagens/b1_enemUp.bmp";
        String spriteBaixo    = "/imagens/b1_enemDown.bmp";
        String spriteEsquerda = "/imagens/b1_enemLeft.bmp";
        String spriteDireita  = "/imagens/b1_enemRigth.bmp"; // Typo intencional: nome do asset original

        if (tipoInimigo == 1) {
            spriteCima     = "/imagens/verdeCima.bmp";
            spriteBaixo    = "/imagens/verdeBaixo.bmp";
            spriteEsquerda = "/imagens/verdeEsquerda.bmp";
            spriteDireita  = "/imagens/verdeDireita.bmp";
        } else if (tipoInimigo == 2) {
            spriteCima     = "/imagens/vermelhoCima.bmp";
            spriteBaixo    = "/imagens/vermelhoBaixo.bmp";
            spriteEsquerda = "/imagens/vermelhoEsquerda.bmp";
            spriteDireita  = "/imagens/vermelhoDireita.bmp";
        }

        up    = carregarImagemComFallback(spriteCima,     "/imagens/b1_enemUp.bmp");
        down  = carregarImagemComFallback(spriteBaixo,    "/imagens/b1_enemDown.bmp");
        left  = carregarImagemComFallback(spriteEsquerda, "/imagens/b1_enemLeft.bmp");
        right = carregarImagemComFallback(spriteDireita,  "/imagens/b1_enemRigth.bmp");
    }

    /**
     * Tenta carregar o sprite do tipo específico; usa o sprite Normal como fallback.
     * Remove o fundo preto com tolerância de cor para suavizar bordas dos BMPs.
     */
    private BufferedImage carregarImagemComFallback(String principal, String fallback) {
        try {
            InputStream stream = getClass().getResourceAsStream(principal);
            if (stream == null) stream = getClass().getResourceAsStream(fallback);
            if (stream != null) return ImageUtils.transformarTransparente(ImageIO.read(stream), Color.BLACK);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // =========================================================================
    // THREAD — LOOP DE MOVIMENTO
    // =========================================================================

    /**
     * Loop principal da thread do inimigo.
     *
     * O bloco {@code synchronized(this)} garante atomicidade entre {@code mover()}
     * e a atualização da hitbox. Sem isso, {@code GamePanel.verificarColisoes()}
     * poderia ler uma hitbox inconsistente durante um movimento parcial.
     */
    @Override
    public void run() {
        while (vivo && gp.rodando) {
            if (gp.estadoJogo == gp.ESTADO_JOGANDO) {
                synchronized (this) {
                    if (!vivo) break; // Segunda verificação dentro do lock — evita mover após morte
                    mover();
                    this.hitbox.x = x + gp.HITBOX_MARGEM;
                    this.hitbox.y = y + gp.HITBOX_MARGEM;
                }
            }
            try {
                Thread.sleep(gp.THREAD_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /** Movimento delegado à thread — existe apenas para cumprir o contrato de Entidade. */
    @Override
    public void atualizar() {}

    // =========================================================================
    // IA DE MOVIMENTO
    // =========================================================================

    @Override
    public void mover() {
        contadorMovimento++;
        contadorTiro++;

        if (contadorMovimento > 120) sortearNovaDirecao(); // Muda de direção a cada ~2 segundos

        int     proximoX = x;
        int     proximoY = y;
        boolean colidiu  = false;

        // Margem de 5px nas bordas do tanque evita que ele raspe em quinas e trave
        final int MARGEM       = 5;
        final int LARGURA_UTIL = 40 - (MARGEM * 2);

        // Look-ahead: verifica os dois cantos da frente antes de mover
        switch (direcao) {
            case "cima" -> {
                proximoY -= velocidade;
                if (gp.gerenciadorMapa.verificarPassagem(proximoX + MARGEM, proximoY) ||
                    gp.gerenciadorMapa.verificarPassagem(proximoX + MARGEM + LARGURA_UTIL, proximoY))
                    colidiu = true;
            }
            case "baixo" -> {
                proximoY += velocidade;
                if (gp.gerenciadorMapa.verificarPassagem(proximoX + MARGEM, proximoY + 39) ||
                    gp.gerenciadorMapa.verificarPassagem(proximoX + MARGEM + LARGURA_UTIL, proximoY + 39))
                    colidiu = true;
            }
            case "esquerda" -> {
                proximoX -= velocidade;
                if (gp.gerenciadorMapa.verificarPassagem(proximoX, proximoY + MARGEM) ||
                    gp.gerenciadorMapa.verificarPassagem(proximoX, proximoY + MARGEM + LARGURA_UTIL))
                    colidiu = true;
            }
            case "direita" -> {
                proximoX += velocidade;
                if (gp.gerenciadorMapa.verificarPassagem(proximoX + 39, proximoY + MARGEM) ||
                    gp.gerenciadorMapa.verificarPassagem(proximoX + 39, proximoY + MARGEM + LARGURA_UTIL))
                    colidiu = true;
            }
        }

        if (proximoX < 0 || proximoX > gp.LARGURA_TELA - 40 ||
            proximoY < 0 || proximoY > gp.ALTURA_TELA  - 40) colidiu = true;

        Rectangle hitboxFutura = new Rectangle(
            proximoX + gp.HITBOX_MARGEM, proximoY + gp.HITBOX_MARGEM,
            gp.HITBOX_TAMANHO, gp.HITBOX_TAMANHO
        );

        if (gp.jogador.isVivo() && hitboxFutura.intersects(gp.jogador.hitbox)) colidiu = true;

        for (TanqueInimigo outro : gp.listaInimigos) {
            if (outro != this && outro.isVivo() && hitboxFutura.intersects(outro.hitbox)) {
                colidiu = true;
                break;
            }
        }

        if (!colidiu) {
            x = proximoX;
            y = proximoY;
        } else {
            sortearNovaDirecao();
            contadorMovimento = 0;
        }

        detectarEAtirar();
    }

    private void sortearNovaDirecao() {
        direcao = switch (random.nextInt(4)) {
            case 0  -> "cima";
            case 1  -> "baixo";
            case 2  -> "esquerda";
            default -> "direita";
        };
        contadorMovimento = 0;
    }

    // =========================================================================
    // LÓGICA DE TIRO
    // =========================================================================

    /**
     * Dispara após um delay mínimo por tipo, com 3% de chance por tick.
     * O delay variável por tipo diferencia o comportamento de cada inimigo.
     */
    private void detectarEAtirar() {
        if (!vivo) return;

        int delayMinimo = switch (tipoInimigo) {
            case 1  -> 180; // Rápido: atira com mais frequência
            case 2  -> 300; // Pesado: atira com menos frequência
            default -> 240; // Normal
        };

        if (contadorTiro > delayMinimo && random.nextInt(100) > 97) {
            efetuarDisparo();
        }
    }

    /**
     * Cria um projétil na posição correta para a direção atual.
     *
     * Por que {@code synchronized}?
     * Garante que x e y são lidos atomicamente. Sem isso, a thread poderia
     * ler x e y em estados diferentes de um mesmo movimento, gerando projéteis
     * em coordenadas inválidas ("tiro fantasma").
     *
     * Limite de 1 projétil ativo por tanque: fiel ao Battle City original e
     * evita acúmulo de threads em dificuldade alta.
     */
    private synchronized void efetuarDisparo() {
        if (!vivo) return;

        // Cancela se este tanque já tem uma bala ativa na tela
        for (Projetil projetil : gp.projeteis) {
            if (projetil.origem == this && projetil.isAtivo()) return;
        }

        int snapshotX = x;
        int snapshotY = y;

        if (snapshotX < 0 || snapshotX > gp.LARGURA_TELA ||
            snapshotY < 0 || snapshotY > gp.ALTURA_TELA) {
            contadorTiro = 0;
            return;
        }

        int centroX = snapshotX + 16;
        int centroY = snapshotY + 16;
        int balaX   = snapshotX;
        int balaY   = snapshotY;

        switch (direcao) {
            case "cima"     -> { balaX = centroX;        balaY = snapshotY - 10; }
            case "baixo"    -> { balaX = centroX;        balaY = snapshotY + 40; }
            case "esquerda" -> { balaX = snapshotX - 10; balaY = centroY;        }
            case "direita"  -> { balaX = snapshotX + 40; balaY = centroY;        }
        }

        gp.adicionarProjetil(new Projetil(gp, balaX, balaY, direcao, this));
        contadorTiro = 0;
    }

    // =========================================================================
    // RENDERIZAÇÃO
    // =========================================================================

    @Override
    public void desenhar(Graphics2D g2) {
        BufferedImage sprite = switch (direcao) {
            case "cima"     -> up;
            case "baixo"    -> down;
            case "esquerda" -> left;
            case "direita"  -> right;
            default         -> down;
        };

        if (sprite != null) {
            g2.drawImage(sprite, x, y, gp.TAMANHO_BLOCO, gp.TAMANHO_BLOCO, null);
        } else {
            // Fallback visual caso o asset não seja carregado
            g2.setColor(Color.RED);
            g2.fillRect(x, y, 40, 40);
        }
    }
}