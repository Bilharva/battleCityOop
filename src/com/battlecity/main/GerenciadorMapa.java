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
 * GerenciadorMapa — responsável pelo mapa de tiles e renderização do cenário
 *
 * responsabilidades:
 *   - rarregar e armazenar a grade de tiles (mapaTileNum)
 *   - carregar imagens dos blocos e o cenário de fundo por fase
 *   - renderizar o mapa em duas camadas (baixo e topo)
 *   - verificar colisões de entidades com o cenário
 *   - localizar posições seguras para spawn de entidades
 *
 * Sistema de tiles:
 *   0 = vazio   (passável)
 *   1 = tijolo  (destrutível, bloqueia)
 *   2 = base    (águia — game over se destruída)
 *   3 = grama   (passável, renderizada no topo cobrindo tanques)
 *   4 = aço     (indestrutível, bloqueia)
 */
public class GerenciadorMapa {

    private final GamePanel gp;

    /** grade bidimensional de IDs de tiles: [coluna][linha] */
    public int[][]  mapaTileNum;

    /** array de tipos de blocos com imagem e flag de colisão */
    public Bloco[]  tiposDeBlocos;

    /** imagem de fundo do cenário atual — renderizada antes dos tiles */
    private BufferedImage imagemFundo;

    // CONSTRUTOR
   
    public GerenciadorMapa(GamePanel gp) {
        this.gp           = gp;
        this.mapaTileNum  = new int[gp.COLUNAS][gp.LINHAS];
        this.tiposDeBlocos = new Bloco[10];

        carregarImagensBlocos();
        // carregarFase() NÃO é chamado aqui — GamePanel.iniciarFase() já faz isso
    }

    // CARREGAMENTO DE FASE

    /**
     * carrega o cenário visual e o mapa de tiles de uma fase
     *
     * tenta carregar um arquivo .txt do classpath (/mapas/faseN.txt)
     * se não encontrar, lança MapaInvalidoException e gera mapa aleatório
     * Ao final, garante que a base (Águia) está corretamente posicionada
     */
    public void carregarFase(int fase) {
        carregarCenario(fase);
        carregarMapa(fase);
        desenharBaseEstatica();
    }

    /** carrega a imagem de fundo correspondente a fase */
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

    /** tenta carregar o arquivo de mapa; usa mapa aleatório como fallback */
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

    // GERAÇÃO DE MAPA ALEATÓRIO

    /**
     * gera um mapa aleatório quando o arquivo de fase não é encontrado
     * preserva áreas fixas: cantos do topo e corredor central da base
     */
    private void gerarMapaAleatorio() {
        Random random = new Random();

        for (int col = 0; col < gp.COLUNAS; col++) {
            for (int lin = 0; lin < gp.LINHAS; lin++) {
                mapaTileNum[col][lin] = 0;

                // preserva áreas fixas importantes
                if (lin == 12 && (col == 4 || col == 8)) continue;         // laterais da base
                if (col >= 5 && col <= 7 && lin >= 11)   continue;         // corredor da base
                if (lin == 0 && (col == 0 || col == 6 || col == 12)) continue; // spawns do topo

                int sorteio = random.nextInt(100);
                if      (sorteio < 10) mapaTileNum[col][lin] = 4; // aço (10%)
                else if (sorteio < 35) mapaTileNum[col][lin] = 1; // tijolo (25%)
                else if (sorteio < 45) mapaTileNum[col][lin] = 3; // grama (10%)
                // else: vazio (55%)
            }
        }
    }

    /**
     * posiciona a base (Águia) e as paredes que a protegem
     * sempre chamado após carregar ou gerar o mapa para garantir
     * que a base está na posição correta independente do arquivo
     */
    private void desenharBaseEstatica() {
        final int COL_BASE = 6;
        final int LIN_BASE = 12;

        mapaTileNum[COL_BASE][LIN_BASE]     = 2; // base
        mapaTileNum[COL_BASE - 1][LIN_BASE] = 1; // parede esquerda
        mapaTileNum[COL_BASE + 1][LIN_BASE] = 1; // parede direita
        mapaTileNum[COL_BASE - 1][LIN_BASE - 1] = 1; // parede superior esquerda
        mapaTileNum[COL_BASE][LIN_BASE - 1]     = 1; // parede superior central
        mapaTileNum[COL_BASE + 1][LIN_BASE - 1] = 1; // parede superior direita
    }

    // CARREGAMENTO DE IMAGENS
    
    private void carregarImagensBlocos() {
        try {
            // Tile 0: vazio — sem imagem, mostra o fundo
            tiposDeBlocos[0] = new Bloco();
            tiposDeBlocos[0].colisao = false;

            // Tile 1: tijolo (destrutível)
            tiposDeBlocos[1] = new Bloco();
            tiposDeBlocos[1].imagem  = carregarImagemSegura("/imagens/long_block.bmp");
            tiposDeBlocos[1].colisao = true;

            // tile 2: base dupla remoção de cor para transparência em BMP
            tiposDeBlocos[2] = new Bloco();
            BufferedImage baseRaw = carregarImagemSegura("/imagens/base.bmp");
            if (baseRaw != null) {
                BufferedImage baseSemPreto = ImageUtils.transformarTransparente(baseRaw, Color.BLACK);
                tiposDeBlocos[2].imagem    = ImageUtils.transformarTransparente(baseSemPreto, Color.WHITE);
            }
            tiposDeBlocos[2].colisao = true;

            // Tile 3: grama passável, renderizada na camada de topo
            tiposDeBlocos[3] = new Bloco();
            BufferedImage gramaRaw = carregarImagemSegura("/imagens/trees2.bmp");
            if (gramaRaw != null) tiposDeBlocos[3].imagem = ImageUtils.transformarTransparente(gramaRaw, Color.WHITE);
            tiposDeBlocos[3].colisao = false;

            // Tile 4: aço (indestrutível)
            tiposDeBlocos[4] = new Bloco();
            tiposDeBlocos[4].imagem  = carregarImagemSegura("/imagens/long_steel.bmp");
            tiposDeBlocos[4].colisao = true;

        } catch (Exception e) {
            System.err.println("ERRO CRÍTICO: falha ao carregar blocos — " + e.getMessage());
        }
    }

    // RENDERIZAÇÃO EM CAMADAS
    
    /**
     * camada inferior: desenha o cenário de fundo e todos os tiles exceto grama
     * tile 0 (vazio) mostra o fundo; os demais são desenhados por cima
     */
    public void desenharCamadaBaixo(Graphics2D g2) {
        // fundo do cenário imagem ou cor sólida como fallback
        if (imagemFundo != null) {
            g2.drawImage(imagemFundo, 0, 0, gp.LARGURA_MAPA, gp.ALTURA_MAPA, null);
        } else {
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, gp.LARGURA_MAPA, gp.ALTURA_MAPA);
        }

        // tiles de chão e paredes (exceto grama, que vai na camada de topo)
        renderizarTiles(g2, id -> id != 0 && id != 3);
    }

    /**
     * camada superior: desenha apenas a grama (tile 3) por cima de tudo,
     * incluindo os tanques — efeito de cobertura do Battle City original
     */
    public void desenharCamadaTopo(Graphics2D g2) {
        renderizarTiles(g2, id -> id == 3);
    }

    /** renderiza apenas os tiles que passam no filtro informado */
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

    // VERIFICAÇÃO DE COLISÃO E SPAWN

    /**
     * verifica se a posição em pixels colide com um tile sólido
     * converte coordenadas de pixel para tile e checa a flag de colisão
     */
    public boolean verificarPassagem(int x, int y) {
        int col = x / gp.TAMANHO_BLOCO;
        int lin = y / gp.TAMANHO_BLOCO;

        if (col < 0 || col >= gp.COLUNAS || lin < 0 || lin >= gp.LINHAS) return true;

        int id = mapaTileNum[col][lin];
        return tiposDeBlocos[id] != null && tiposDeBlocos[id].colisao;
    }

    /**
     * localiza coordenadas de pixel seguras para spawn (usada pelo jogador)
     * garante que o tile é passável e tem pelo menos uma saída livre
     */
    public int[] encontrarEspacoVazio(boolean aleatorio) {
        if (aleatorio) {
            for (int tentativa = 0; tentativa < 1000; tentativa++) {
                int col = (int)(Math.random() * gp.COLUNAS);
                int lin = (int)(Math.random() * gp.LINHAS);

                // apenas tiles passáveis entre as linhas 3 e 10 (longe do topo e da base)
                boolean localValido = (mapaTileNum[col][lin] == 0 || mapaTileNum[col][lin] == 3)
                                      && lin > 2 && lin < 11;

                if (localValido && temSaidaLivre(col, lin)) {
                    return new int[]{col * gp.TAMANHO_BLOCO, lin * gp.TAMANHO_BLOCO};
                }
            }
        }

        // fallback: retorna posição padrão próxima à base
        System.out.println("ALERTA: Spawn seguro não encontrado. Usando coordenadas padrão.");
        return new int[]{4 * gp.TAMANHO_BLOCO, 12 * gp.TAMANHO_BLOCO};
    }

    /** verifica se há pelo menos uma saída passável nos 4 vizinhos do tile */
    private boolean temSaidaLivre(int col, int lin) {
        return isTilePassavel(col, lin - 1) ||
               isTilePassavel(col, lin + 1) ||
               isTilePassavel(col - 1, lin) ||
               isTilePassavel(col + 1, lin);
    }

    /** retorna true se o tile na posição informada permite movimentação de tanques */
    private boolean isTilePassavel(int col, int lin) {
        if (col < 0 || col >= gp.COLUNAS || lin < 0 || lin >= gp.LINHAS) return false;
        int tile = mapaTileNum[col][lin];
        return tile == 0 || tile == 3; // Vazio ou grama
    }

    // carrega uma imagem do classpath sem lançar exceção — retorna null se falhar
    private BufferedImage carregarImagemSegura(String caminho) {
        try {
            InputStream stream = getClass().getResourceAsStream(caminho);
            return (stream == null) ? null : ImageIO.read(stream);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * destrói um tijolo e retorna true se houve colisão
     * chamado por projéteis ao atingir tiles destrutíveis
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
        return id == 4 || id == 2; // aço e base bloqueiam mas não são destruídos aqui
    }
}