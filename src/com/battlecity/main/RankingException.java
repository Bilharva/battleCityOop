package com.battlecity.main;

/**
 * lançada quando uma linha do arquivo ranking.txt está corrompida
 * Capturada dentro do loop de leitura para que linhas inválidas sejam ignoradas
 * sem interromper o carregamento das demais entradas
 */
public class RankingException extends Exception {

    public RankingException(String mensagem) {
        super(mensagem);
    }

    public RankingException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}