package com.battlecity.entities;

import com.battlecity.main.GamePanel;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/**
 * Animação de explosão exibida ao destruir um tanque inimigo.
 *
 * Exibe 5 frames sequencialmente via contagem de frames do game loop,
 * sem thread própria — a atualização é feita pelo GamePanel a cada tick.
 * Ao final da animação, {@code ativa} é definido como {@code false} e
 * o GamePanel remove a instância da lista automaticamente.
 */
public class Explosao {

    private final GamePanel gp;

    public final int x, y;
    public boolean ativa = true;

    private static final int TOTAL_FRAMES    = 5;
    private static final int DURACAO_FRAME   = 4; // Ticks do game loop por frame de animação

    private final BufferedImage[] frames = new BufferedImage[TOTAL_FRAMES];
    private int frameAtual    = 0;
    private int contadorFrame = 0;

    // =========================================================================
    // CONSTRUTOR
    // =========================================================================

    public Explosao(GamePanel gp, int x, int y) {
        this.gp = gp;
        // Centraliza a explosão (64px) sobre o tanque destruído (40px)
        this.x  = x - 12;
        this.y  = y - 12;
        carregarFrames();
    }

    private void carregarFrames() {
        for (int i = 0; i < TOTAL_FRAMES; i++) {
            try {
                var stream = getClass().getResourceAsStream("/imagens/bigExplosion" + (i + 1) + ".png");
                if (stream != null) frames[i] = ImageIO.read(stream);
            } catch (Exception e) {
                System.err.println("Erro ao carregar frame de explosão " + (i + 1));
            }
        }
    }

    // =========================================================================
    // ATUALIZAÇÃO E RENDERIZAÇÃO
    // =========================================================================

    /** Avança a animação a cada tick. Desativa a explosão ao terminar o último frame. */
    public void atualizar() {
        if (!ativa) return;

        contadorFrame++;
        if (contadorFrame >= DURACAO_FRAME) {
            contadorFrame = 0;
            frameAtual++;
            if (frameAtual >= TOTAL_FRAMES) {
                ativa = false;
            }
        }
    }

    public void desenhar(Graphics2D g2) {
        if (!ativa || frameAtual >= TOTAL_FRAMES) return;

        BufferedImage frame = frames[frameAtual];
        if (frame != null) {
            g2.drawImage(frame, x, y, 64, 64, null);
        }
    }
}