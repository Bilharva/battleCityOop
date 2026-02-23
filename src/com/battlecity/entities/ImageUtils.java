package com.battlecity.entities;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * classe para processamento de imagens.
 *
 * BMPs (formato usado nos tanques, blocos e base não suportam transparência nativamente
 * essa classe remove essa cor de fundo
 * pixel a pixel, criando uma nova imagem ARGB com transparência real.
 */
public class ImageUtils {

    /**
     * remove a cor de fundo de uma imagem com tolerância padrão de 30 pontos
     * A tolerância resolve o problema de bordas levemente escuras em BMPs,
     * onde os pixels próximos ao fundo não são exatamente a cor alvo.
     */
    public static BufferedImage transformarTransparente(BufferedImage imagem, Color corFundo) {
        return transformarTransparenteComTolerancia(imagem, corFundo, 30);
    }

    /**
     * remove pixels cuja cor está dentro da tolerância informada, tornando-os transparentes.
     *
     * @param imagem     Imagem de origem (geralmente um BMP carregado do classpath).
     * @param corFundo   Cor de fundo a ser removida (preto para inimigos, branco para o jogador).
     * @param tolerancia Distância máxima de cada canal RGB para ser considerado "fundo".
     * @return Nova imagem ARGB com o fundo removido, ou null se a entrada for null.
     */
    public static BufferedImage transformarTransparenteComTolerancia(BufferedImage imagem, Color corFundo, int tolerancia) {
        if (imagem == null) return null;

        // cria cópia no formato ARGB — o formato original BMP não tem canal alpha
        BufferedImage resultado = new BufferedImage(imagem.getWidth(), imagem.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resultado.createGraphics();
        g2.drawImage(imagem, 0, 0, null);
        g2.dispose();

        int rAlvo = corFundo.getRed();
        int gAlvo = corFundo.getGreen();
        int bAlvo = corFundo.getBlue();

        // varre pixel a pixel: qualquer pixel dentro da tolerância vira transparente (alpha = 0)
        for (int x = 0; x < resultado.getWidth(); x++) {
            for (int y = 0; y < resultado.getHeight(); y++) {
                int pixel = resultado.getRGB(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >>  8) & 0xFF;
                int b =  pixel        & 0xFF;

                if (Math.abs(r - rAlvo) <= tolerancia &&
                    Math.abs(g - gAlvo) <= tolerancia &&
                    Math.abs(b - bAlvo) <= tolerancia) {
                    resultado.setRGB(x, y, 0x00FFFFFF); // alpha 0 = completamente transparente
                }
            }
        }
        return resultado;
    }
}