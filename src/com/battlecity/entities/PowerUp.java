package com.battlecity.entities;

import com.battlecity.main.GamePanel;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/**
 * power-up de vida extra.
 *
 * cria na posição onde um inimigo foi destruído e desaparece após 8 segundos.
 * os últimos 2 segundos pisca para avisar o jogador que vai sumir.
 * colisão é detectada por {@code GamePanel.verificarColisoes()}.
 */
public class PowerUp {

    private final GamePanel gp;

    public int     x, y;
    public boolean ativo = true;

    // sprite do power-up carregado uma vez no construtor
    private BufferedImage imagemVida;

    private static final long DURACAO_MS      = 8_000; // tempo total na tela (ms)
    private static final long AVISO_PISCAR_MS = 2_000; // começa a piscar neste tempo restante
    private static final int  INTERVALO_PISCA = 200;   // período do piscar (ms)

    private final long tempoCriacao = System.currentTimeMillis();
    // CONSTRUTOR

    public PowerUp(GamePanel gp, int x, int y) {
        this.gp = gp;
        this.x  = x;
        this.y  = y;
        carregarImagem();
    }

    private void carregarImagem() {
        try {
            var stream = getClass().getResourceAsStream("/imagens/vida.png");
            if (stream != null)
                imagemVida = ImageUtils.transformarTransparente(ImageIO.read(stream), Color.WHITE);
        } catch (Exception e) {
            System.err.println("Erro ao carregar vida.png — usando fallback gráfico.");
        }
    }

    // hitbox menor que o tile para exigir contato mais preciso ao coletar
    public Rectangle getHitbox() {
        return new Rectangle(x + 5, y + 5, 30, 30);
    }

    // ATUALIZAÇÃO E RENDERIZAÇÃO

    //desativa o power-up após DURACAO_MS. chamado a cada frame pelo GamePanel
    public void atualizar() {
        if (System.currentTimeMillis() - tempoCriacao > DURACAO_MS) {
            ativo = false;
        }
    }

    public void desenhar(Graphics2D g2) {
        if (!ativo) return;

        // Ppsca nos últimos AVISO_PISCAR_MS para sinalizar que vai sumir
        long tempoRestante = DURACAO_MS - (System.currentTimeMillis() - tempoCriacao);
        boolean visivelNoPiscar = (System.currentTimeMillis() / INTERVALO_PISCA) % 2 == 0;
        if (tempoRestante < AVISO_PISCAR_MS && visivelNoPiscar) return;

        // imagem de vida — ou fallback com caixa dourada se a imagem não carregou
        if (imagemVida != null) {
            g2.drawImage(imagemVida, x, y, 40, 40, null);
        } else {
            g2.setColor(new Color(255, 215, 0));
            g2.fillRoundRect(x + 4, y + 4, 32, 32, 8, 8);
            g2.setColor(Color.RED);
            g2.setFont(new Font("Arial", Font.BOLD, 20));
            g2.drawString("♥", x + 8, y + 28);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2));
            g2.drawRoundRect(x + 4, y + 4, 32, 32, 8, 8);
        }
    }
}