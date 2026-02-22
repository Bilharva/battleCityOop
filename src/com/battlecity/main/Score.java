package com.battlecity.main;

/**
 * Representa uma entrada no ranking: nome do jogador e pontuação.
 * Implementa Comparable para permitir ordenação automática decrescente
 * via Collections.sort() — do maior para o menor score.
 */
public class Score implements Comparable<Score> {

    private final String nome;
    private final int    pontos;

    public Score(String nome, int pontos) {
        this.nome   = nome;
        this.pontos = pontos;
    }

    public String getNome()   { return nome; }
    public int    getPontos() { return pontos; }

    /** Ordenação decrescente: scores maiores aparecem primeiro na lista. */
    @Override
    public int compareTo(Score outro) {
        return outro.pontos - this.pontos;
    }

    /** Formato de persistência: "NOME;PONTOS" — lido por GerenciadorRanking. */
    @Override
    public String toString() {
        return nome + ";" + pontos;
    }
}