package com.battlecity.main;

/**
 * representa uma entrada no ranking: nome do jogador e pontuação
 * implementa Comparable para permitir ordenação automática decrescente
 * via Collections.sort() — do maior para o menor score
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

    /** ordenação decrescente: scores maiores aparecem primeiro na lista */
    @Override
    public int compareTo(Score outro) {
        return outro.pontos - this.pontos;
    }

    /** formato de persistência: "NOME;PONTOS" — lido por GerenciadorRanking */
    @Override
    public String toString() {
        return nome + ";" + pontos;
    }
}