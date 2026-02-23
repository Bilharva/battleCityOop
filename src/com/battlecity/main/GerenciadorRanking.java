package com.battlecity.main;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * gerencia o ranking de pontuações
 *
 * lê e escreve o arquivo "ranking.txt" no diretório de execução
 * cada linha segue o formato "NOME;PONTOS". Linhas corrompidas disparam
 * rankingException e são ignoradas individualmente sem interromper a leitura
 */
public class GerenciadorRanking {

    private static final String ARQUIVO_RANKING = "ranking.txt";
    private static final int    LIMITE_TOP       = 10;

    // LEITURA

    /**
     * carrega e retorna o ranking ordenado do arquivo
     * retorna lista vazia se o arquivo não existir
     */
    public static List<Score> carregarRanking() {
        List<Score> lista   = new ArrayList<>();
        File        arquivo = new File(ARQUIVO_RANKING);

        if (!arquivo.exists()) return lista;

        try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                // cada linha é tratada isoladamente — erro em uma não cancela as demais
                try {
                    lista.add(parsearLinha(linha));
                } catch (RankingException e) {
                    System.err.println("AVISO ranking: " + e.getMessage() + " — linha ignorada.");
                } catch (NumberFormatException e) {
                    System.err.println("AVISO ranking: pontuação inválida em \"" + linha + "\" — linha ignorada.");
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao ler ranking: " + e.getMessage());
        }

        Collections.sort(lista);
        return lista;
    }

    /**
     * interpreta uma linha do arquivo e retorna um Score.
     * @throws RankingException se a linha não estiver no formato "NOME;PONTOS".
     */
    private static Score parsearLinha(String linha) throws RankingException {
        String[] partes = linha.split(";");
        if (partes.length != 2) {
            throw new RankingException("Linha corrompida no ranking: \"" + linha + "\"");
        }
        return new Score(partes[0], Integer.parseInt(partes[1]));
    }

    // ESCRITA
    
    /**
     * adiciona um novo score ao ranking, mantendo apenas o top 10.
     * carrega, insere, ordena, corta e persiste em um único fluxo.
     */
    public static void adicionarScore(String nome, int pontos) {
        List<Score> lista = carregarRanking();
        lista.add(new Score(nome, pontos));
        Collections.sort(lista);

        if (lista.size() > LIMITE_TOP) {
            lista = lista.subList(0, LIMITE_TOP);
        }

        salvarArquivo(lista);
    }

    private static void salvarArquivo(List<Score> lista) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(ARQUIVO_RANKING))) {
            for (Score score : lista) {
                writer.write(score.toString());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Erro ao salvar ranking: " + e.getMessage());
        }
    }
}