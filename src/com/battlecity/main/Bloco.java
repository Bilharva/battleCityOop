package com.battlecity.main;

import java.awt.image.BufferedImage;

/**
 * Representa um tipo de tile do mapa.
 * Armazena a imagem visual e se o tile bloqueia a passagem de entidades.
 */
public class Bloco {
    public BufferedImage imagem;
    public boolean colisao = false;
}