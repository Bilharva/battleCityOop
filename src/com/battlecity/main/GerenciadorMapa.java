package com.battlecity.main;

import com.battlecity.entities.ImageUtils;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * GerenciadorMapa — Responsável pelo mapa de tiles e renderização do cenário.
 *
 * Responsabilidades:
 *   - Carregar e armazenar a grade de tiles (mapaTileNum)
 *   - Carregar imagens dos blocos e o cenário de fundo por fase
 *   - Renderizar o mapa em duas camadas (baixo e topo)
 *   - Verificar colisões de entidades com o cenário
 *   - Localizar posições seguras para spawn de entidades
 *
 * Sistema de tiles:
 *   0 = Vazio   (passável)
 *   1 = Tijolo  (destrutível, bloqueia)
 *   2 = Base    (águia — game over se destruída)
 *   3 = Grama   (passável, renderizada no topo cobrindo tanques)
 *   4 = Aço     (indestrutível, bloqueia)
 */
public class GerenciadorMapa {

    private final GamePanel gp;

    /** Grade bidimensional de IDs de tiles: [coluna][linha]. */
    public int[][]  mapaTileNum;

    /** Array de tipos de blocos com imagem e flag de colisão. */
    public Bloco[]  tiposDeBlocos;

    /** Imagem de fundo do cenário atual — renderizada antes dos tiles. */
    private BufferedImage imagemFundo;

    // =========================================================================
    // CONSTRUTOR
    // =========================================================================

    public GerenciadorMapa(GamePanel gp) {
        this.gp           = gp;
        this.mapaTileNum  = new int[gp.COLUNAS][gp.LINHAS];
        this.tiposDeBlocos = new Bloco[10];

        carregarImagensBlocos();
        // carregarFase() NÃO é chamado aqui — GamePanel.iniciarFase() já faz isso.
    }

    // =========================================================================
    // CARREGAMENTO DE FASE
    // =========================================================================

    /**
     * Carrega o cenário visual e o mapa de tiles de uma fase.
     *
     * Tenta carregar um arquivo .txt do classpath (/mapas/faseN.txt).
     * Se não encontrar, lança MapaInvalidoException e gera mapa aleatório.
     * Ao final, garante que a base (Águia) está corretamente posicionada.
     */
    public void carregarFase(int fase) {
        carregarCenario(fase);
        carregarMapa(fase);
        desenharBaseEstatica();
    }

    /** Carrega a imagem de fundo correspondente à fase. */
    private void carregarCenario(int fase) {
        String caminhoCenario = switch (fase) {
            case 1  -> "/imagens/cenarioUrbano.png";
            case 2  -> "/imagens/cenarioGrama.png";
            case 3  -> "/imagens/cenarioDeserto.png";
            case 4  -> "/imagens/cenarioGelo.png";
            default -> "/imagens/cenarioFuturista.png";
        };

        imagemFundo = carregarImagemSegura(caminhoCenario);

        if (imagemFundo == null)
            System.err.println("AVISO: Cenário não encontrado: " + caminhoCenario);
        else
            System.out.println("Cenário carregado: " + caminhoCenario);
    }

    /** Tenta carregar o arquivo de mapa; usa mapa aleatório como fallback. */
    private void carregarMapa(int fase) {
        String caminho = "/mapas/fase" + fase + ".txt";
        InputStream stream = getClass().getResourceAsStream(caminho);

        try {
            if (stream == null) {
                throw new MapaInvalidoException("Arquivo de mapa não encontrado: " + caminho);
            }
            carregarArquivoMapa(stream);
        } catch (MapaInvalidoException e) {
            System.err.println("AVISO: " + e.getMessage() + " — gerando mapa aleatório.");
            gerarMapaAleatorio();
        }
    }

    private void carregarArquivoMapa(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            for (int lin = 0; lin < gp.LINHAS; lin++) {
                String linha = reader.readLine();
                if (linha == null) break;
                String[] valores = linha.split(" ");
                for (int col = 0; col < gp.COLUNAS; col++) {
                    mapaTileNum[col][lin] = Integer.parseInt(valores[col]);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================================================================
    // GERAÇÃO DE MAPA ALEATÓRIO
    // =========================================================================

    /**
     * Gera um mapa aleatório quando o arquivo de fase não é encontrado.
     * Preserva áreas fixas: cantos do topo e corredor central da base.
     */
    private void gerarMapaAleatorio() {
        Random random = new Random();

        for (int col = 0; col < gp.COLUNAS; col++) {
            for (int lin = 0; lin < gp.LINHAS; lin++) {
                mapaTileNum[col][lin] = 0;

                // Preserva áreas fixas importantes
                if (lin == 12 && (col == 4 || col == 8)) continue;         // Laterais da base
                if (col >= 5 && col <= 7 && lin >= 11)   continue;         // Corredor da base
                if (lin == 0 && (col == 0 || col == 6 || col == 12)) continue; // Spawns do topo

                int sorteio = random.nextInt(100);
                if      (sorteio < 10) mapaTileNum[col][lin] = 4; // Aço (10%)
                else if (sorteio < 35) mapaTileNum[col][lin] = 1; // Tijolo (25%)
                else if (sorteio < 45) mapaTileNum[col][lin] = 3; // Grama (10%)
                // else: vazio (55%)
            }
        }
    }

    /**
     * Posiciona a base (Águia) e as paredes que a protegem.
     * Sempre chamado após carregar ou gerar o mapa para garantir
     * que a base está na posição correta independente do arquivo.
     */
    private void desenharBaseEstatica() {
        final int COL_BASE = 6;
        final int LIN_BASE = 12;

        mapaTileNum[COL_BASE][LIN_BASE]     = 2; // Base (Águia)
        mapaTileNum[COL_BASE - 1][LIN_BASE] = 1; // Parede esquerda
        mapaTileNum[COL_BASE + 1][LIN_BASE] = 1; // Parede direita
        mapaTileNum[COL_BASE - 1][LIN_BASE - 1] = 1; // Parede superior esquerda
        mapaTileNum[COL_BASE][LIN_BASE - 1]     = 1; // Parede superior central
        mapaTileNum[COL_BASE + 1][LIN_BASE - 1] = 1; // Parede superior direita
    }

    // =========================================================================
    // CARREGAMENTO DE IMAGENS
    // =========================================================================

    private void carregarImagensBlocos() {
        try {
            // Tile 0: Vazio — sem imagem, mostra o fundo
            tiposDeBlocos[0] = new Bloco();
            tiposDeBlocos[0].colisao = false;

            // Tile 1: Tijolo (destrutível)
            tiposDeBlocos[1] = new Bloco();
            tiposDeBlocos[1].imagem  = carregarImagemSegura("/imagens/long_block.bmp");
            tiposDeBlocos[1].colisao = true;

            // Tile 2: Base (Águia) — dupla remoção de cor para transparência em BMP
            tiposDeBlocos[2] = new Bloco();
            BufferedImage baseRaw = carregarImagemSegura("/imagens/base.bmp");
            if (baseRaw != null) {
                BufferedImage baseSemPreto = ImageUtils.transformarTransparente(baseRaw, Color.BLACK);
                tiposDeBlocos[2].imagem    = ImageUtils.transformarTransparente(baseSemPreto, Color.WHITE);
            }
            tiposDeBlocos[2].colisao = true;

            // Tile 3: Grama — passável, renderizada na camada de topo
            tiposDeBlocos[3] = new Bloco();
            BufferedImage gramaRaw = carregarImagemSegura("/imagens/trees2.bmp");
            if (gramaRaw != null) tiposDeBlocos[3].imagem = ImageUtils.transformarTransparente(gramaRaw, Color.WHITE);
            tiposDeBlocos[3].colisao = false;

            // Tile 4: Aço (indestrutível)
            tiposDeBlocos[4] = new Bloco();
            tiposDeBlocos[4].imagem  = carregarImagemSegura("/imagens/long_steel.bmp");
            tiposDeBlocos[4].colisao = true;

        } catch (Exception e) {
            System.err.println("ERRO CRÍTICO: falha ao carregar blocos — " + e.getMessage());
        }
    }

    // =========================================================================
    // RENDERIZAÇÃO EM CAMADAS
    // =========================================================================

    /**
     * Camada inferior: desenha o cenário de fundo e todos os tiles exceto grama.
     * Tile 0 (vazio) mostra o fundo; os demais são desenhados por cima.
     */
    public void desenharCamadaBaixo(Graphics2D g2) {
        // Fundo do cenário — imagem ou cor sólida como fallback
        if (imagemFundo != null) {
            g2.drawImage(imagemFundo, 0, 0, gp.LARGURA_MAPA, gp.ALTURA_MAPA, null);
        } else {
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, gp.LARGURA_MAPA, gp.ALTURA_MAPA);
        }

        // Tiles de chão e paredes (exceto grama, que vai na camada de topo)
        renderizarTiles(g2, id -> id != 0 && id != 3);
    }

    /**
     * Camada superior: desenha apenas a grama (tile 3) por cima de tudo,
     * incluindo os tanques — efeito de cobertura do Battle City original.
     */
    public void desenharCamadaTopo(Graphics2D g2) {
        renderizarTiles(g2, id -> id == 3);
    }

    /** Renderiza apenas os tiles que passam no filtro informado. */
    private void renderizarTiles(Graphics2D g2, java.util.function.Predicate<Integer> filtro) {
        for (int col = 0; col < gp.COLUNAS; col++) {
            for (int lin = 0; lin < gp.LINHAS; lin++) {
                int id = mapaTileNum[col][lin];
                if (filtro.test(id) && tiposDeBlocos[id] != null && tiposDeBlocos[id].imagem != null) {
                    g2.drawImage(tiposDeBlocos[id].imagem,
                                 col * gp.TAMANHO_BLOCO, lin * gp.TAMANHO_BLOCO,
                                 gp.TAMANHO_BLOCO, gp.TAMANHO_BLOCO, null);
                }
            }
        }
    }

    // =========================================================================
    // VERIFICAÇÃO DE COLISÃO E SPAWN
    // =========================================================================

    /**
     * Verifica se a posição em pixels colide com um tile sólido.
     * Converte coordenadas de pixel para tile e checa a flag de colisão.
     */
    public boolean verificarPassagem(int x, int y) {
        int col = x / gp.TAMANHO_BLOCO;
        int lin = y / gp.TAMANHO_BLOCO;

        if (col < 0 || col >= gp.COLUNAS || lin < 0 || lin >= gp.LINHAS) return true;

        int id = mapaTileNum[col][lin];
        return tiposDeBlocos[id] != null && tiposDeBlocos[id].colisao;
    }

    /**
     * Localiza coordenadas de pixel seguras para spawn (usada pelo jogador).
     * Garante que o tile é passável e tem pelo menos uma saída livre.
     */
    public int[] encontrarEspacoVazio(boolean aleatorio) {
        if (aleatorio) {
            for (int tentativa = 0; tentativa < 1000; tentativa++) {
                int col = (int)(Math.random() * gp.COLUNAS);
                int lin = (int)(Math.random() * gp.LINHAS);

                // Apenas tiles passáveis entre as linhas 3 e 10 (longe do topo e da base)
                boolean localValido = (mapaTileNum[col][lin] == 0 || mapaTileNum[col][lin] == 3)
                                      && lin > 2 && lin < 11;

                if (localValido && temSaidaLivre(col, lin)) {
                    return new int[]{col * gp.TAMANHO_BLOCO, lin * gp.TAMANHO_BLOCO};
                }
            }
        }

        // Fallback: retorna posição padrão próxima à base
        System.out.println("ALERTA: Spawn seguro não encontrado. Usando coordenadas padrão.");
        return new int[]{4 * gp.TAMANHO_BLOCO, 12 * gp.TAMANHO_BLOCO};
    }

    /** Verifica se há pelo menos uma saída passável nos 4 vizinhos do tile. */
    private boolean temSaidaLivre(int col, int lin) {
        return isTilePassavel(col, lin - 1) ||
               isTilePassavel(col, lin + 1) ||
               isTilePassavel(col - 1, lin) ||
               isTilePassavel(col + 1, lin);
    }

    /** Retorna true se o tile na posição informada permite movimentação de tanques. */
    private boolean isTilePassavel(int col, int lin) {
        if (col < 0 || col >= gp.COLUNAS || lin < 0 || lin >= gp.LINHAS) return false;
        int tile = mapaTileNum[col][lin];
        return tile == 0 || tile == 3; // Vazio ou grama
    }

    /** Carrega uma imagem do classpath sem lançar exceção — retorna null se falhar. */
    private BufferedImage carregarImagemSegura(String caminho) {
        try {
            InputStream stream = getClass().getResourceAsStream(caminho);
            return (stream == null) ? null : ImageIO.read(stream);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Destrói um tijolo e retorna true se houve colisão.
     * Chamado por projéteis ao atingir tiles destrutíveis.
     */
    public boolean verificarColisaoETransformar(int x, int y) {
        int col = x / gp.TAMANHO_BLOCO;
        int lin = y / gp.TAMANHO_BLOCO;

        if (col < 0 || col >= gp.COLUNAS || lin < 0 || lin >= gp.LINHAS) return true;

        int id = mapaTileNum[col][lin];
        if (id == 1) {
            mapaTileNum[col][lin] = 0;
            gp.adicionarPontos(10);
            return true;
        }
        return id == 4 || id == 2; // Aço e base bloqueiam mas não são destruídos aqui
    }
}