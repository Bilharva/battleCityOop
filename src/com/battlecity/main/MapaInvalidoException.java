package com.battlecity.main;

/**
 * Lançada quando um arquivo de mapa não é encontrado ou está malformado.
 * Permite tratar falhas de I/O de mapa de forma semântica e aplicar
 * o fallback de geração aleatória no GerenciadorMapa.
 */
public class MapaInvalidoException extends Exception {

    public MapaInvalidoException(String mensagem) {
        super(mensagem);
    }

    public MapaInvalidoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}