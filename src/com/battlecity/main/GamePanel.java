package com.battlecity.main;

import com.battlecity.entities.Explosao;
import com.battlecity.entities.ImageUtils;
import com.battlecity.entities.PowerUp;
import com.battlecity.entities.Projetil;
import com.battlecity.entities.TanqueInimigo;
import com.battlecity.entities.TanqueJogador;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * GamePanel — Motor central do jogo (Game Loop + Física + Renderização).
 *
 * Responsabilidades:
 *   - Executar o game loop a 60 FPS via Thread (implements Runnable)
 *   - Gerenciar o ciclo de vida das fases (iniciar, reiniciar, concluir)
 *   - Detectar e resolver colisões entre todas as entidades
 *   - Renderizar o mundo em camadas (fundo → entidades → HUD → overlays)
 *
 * Nota de design: esta classe concentra muitas responsabilidades por ser o
 * ponto de integração do jogo. Em um projeto maior, seria dividida em
 * subcomponentes (FisicaEngine, RenderEngine, etc.).
 */
public class GamePanel extends JPanel implements Runnable {

    // =========================================================================
    // CONSTANTES DE GEOMETRIA
    // =========================================================================

    public final int TAMANHO_BLOCO = 40;              // Tamanho de cada tile em pixels
    public final int COLUNAS       = 13;              // Largura do mapa em tiles
    public final int LINHAS        = 13;              // Altura do mapa em tiles
    public final int LARGURA_MAPA  = TAMANHO_BLOCO * COLUNAS;
    public final int ALTURA_MAPA   = TAMANHO_BLOCO * LINHAS;
    public final int LARGURA_HUD   = 200;             // Painel lateral de informações
    public final int LARGURA_TELA  = LARGURA_MAPA + LARGURA_HUD;
    public final int ALTURA_TELA   = ALTURA_MAPA;

    // =========================================================================
    // CONSTANTES DE GAMEPLAY
    // =========================================================================

    /** Hitbox 2px menor que o sprite evita o tanque travar em quinas de paredes. */
    public final int HITBOX_TAMANHO  = 36;
    public final int HITBOX_MARGEM   = 2;

    /** HP começa em 100. Cada tiro causa 25 de dano → 4 tiros = morte. */
    public final int HP_MAXIMO       = 100;
    public final int DANO_TIRO       = 25;

    public final int PONTOS_POR_KILL = 100;  // Pontos ganhos ao destruir um inimigo
    public final int THREAD_SLEEP_MS = 16;   // ~60fps para threads dos tanques

    // =========================================================================
    // ESTADOS DO JOGO — máquina de estados usada em atualizar()
    // =========================================================================

    public final int ESTADO_JOGANDO  = 1;
    public final int ESTADO_PAUSADO  = 2;
    public final int ESTADO_MORTE    = 3;
    public final int ESTADO_GAMEOVER = 4;
    public final int ESTADO_VITORIA  = 5;

    // =========================================================================
    // MOTOR DO GAME LOOP
    // =========================================================================

    /**
     * Nanosegundos por segundo — usado no cálculo do intervalo do game loop.
     * intervalo = NANO_POR_SEGUNDO / FPS
     */
    private static final double NANO_POR_SEGUNDO = 1_000_000_000.0;

    public final int FPS   = 60;
    private Thread  gameThread;
    public  boolean rodando = false;

    // =========================================================================
    // DEPENDÊNCIAS E SISTEMAS
    // =========================================================================

    private final Main             main;
    public        ManipuladorTeclas teclaH         = new ManipuladorTeclas();
    public        GerenciadorMapa   gerenciadorMapa;

    /** Instância compartilhada — evitar criar um new Random() por frame. */
    private final Random random = new Random();

    // =========================================================================
    // DADOS DA SESSÃO DE JOGO
    // =========================================================================

    public String  nomeJogador;
    public int     dificuldade;        // 0 = Fácil | 1 = Médio | 2 = Difícil
    public int     pontuacaoTotal  = 0;
    public int     pontuacaoFase   = 0;
    public int     faseAtual       = 1;
    public final int MAX_FASES     = 5;

    private boolean scoreSalvo        = false;
    public  int     totalInimigosFase = 10;
    public  int     inimigosMortos    = 0;

    // =========================================================================
    // ESTADO DA UI / PAUSA
    // =========================================================================

    public  int     estadoJogo;
    public  int     comandoNum = 0;    // Índice da opção selecionada no menu de pausa
    private boolean escTrava   = false; // Evita que ESC alterne infinitamente

    // =========================================================================
    // ASSETS DE OVERLAY
    // =========================================================================

    private BufferedImage imgAvisoMorte, imgGameOver, imgWon;

    // =========================================================================
    // ENTIDADES DO MUNDO
    // =========================================================================

    /**
     * CopyOnWriteArrayList: thread-safe para iteração simultânea por múltiplas
     * threads (game loop + threads de tanques + threads de projéteis).
     * Custo: cópia a cada escrita — aceitável dado o tamanho pequeno das listas.
     */
    public TanqueJogador       jogador;
    public List<TanqueInimigo> listaInimigos = new CopyOnWriteArrayList<>();
    public List<Projetil>      projeteis     = new CopyOnWriteArrayList<>();
    public List<PowerUp>       powerUps      = new CopyOnWriteArrayList<>();
    public List<Explosao>      explosoes     = new CopyOnWriteArrayList<>();

    // =========================================================================
    // CONSTRUTOR
    // =========================================================================

    public GamePanel(Main main, String nome, int dificuldade) {
        this.main        = main;
        this.nomeJogador = (nome == null || nome.isEmpty()) ? "RECRUTA" : nome;
        this.dificuldade = dificuldade;

        // Configuração do painel Swing
        this.setPreferredSize(new Dimension(LARGURA_TELA, ALTURA_TELA));
        this.setBackground(Color.BLACK);
        this.setDoubleBuffered(true); // Elimina flickering durante repaint()
        this.addKeyListener(teclaH);
        this.setFocusable(true);
        this.requestFocus();

        carregarImagensExtras();
        this.gerenciadorMapa = new GerenciadorMapa(this);
        this.jogador         = new TanqueJogador(this, teclaH);
        this.jogador.vidas   = 5;
        this.jogador.hp      = HP_MAXIMO;
        this.estadoJogo      = ESTADO_JOGANDO;

        // rodando=true antes de iniciarFase() garante que as threads dos
        // TanqueInimigo criadas dentro de iniciarFase() já encontrem o jogo ativo.
        rodando = true;
        iniciarFase();
        iniciarThreadJogo();
    }

    // =========================================================================
    // CICLO DE VIDA DAS FASES
    // =========================================================================

    /** Reinicia o jogo do zero: fase 1, pontuação zerada, 5 vidas. */
    public void reiniciarJogoCompleto() {
        faseAtual      = 1;
        pontuacaoTotal = 0;
        scoreSalvo     = false;

        if (jogador != null) {
            jogador.vidas = 5;
            jogador.hp    = HP_MAXIMO;
            jogador.setVivo(true);
        }
        iniciarFase();
    }

    /** Reinicia a fase atual mantendo pontuação total e número de vidas. */
    public void reiniciarFaseAtual() {
        pontuacaoFase = 0;

        if (jogador != null) {
            jogador.hp = HP_MAXIMO;
            jogador.setVivo(true);
        }
        iniciarFase();
    }

    /**
     * Chamado quando todos os inimigos da fase foram destruídos.
     * Calcula bônus, avança a fase ou aciona tela de vitória.
     */
    private void concluirMissao() {
        calcularBonusFimDeFase();
        faseAtual++;

        if (faseAtual <= MAX_FASES) {
            iniciarFase();
        } else {
            // Todas as fases concluídas: salva score e exibe vitória
            estadoJogo = ESTADO_VITORIA;
            if (!scoreSalvo) {
                GerenciadorRanking.adicionarScore(nomeJogador, pontuacaoTotal);
                scoreSalvo = true;
            }
        }
    }

    /**
     * Prepara o estado completo de uma fase:
     *   1. Define quantidade de inimigos por dificuldade
     *   2. Mata threads dos inimigos antigos antes de limpar as listas
     *   3. Recarrega o mapa
     *   4. Reposiciona jogador e spawna novos inimigos
     */
    public void iniciarFase() {
        inimigosMortos = 0;

        totalInimigosFase = switch (dificuldade) {
            case 0  -> 6  + (faseAtual * 2);  // Fácil
            case 1  -> 10 + (faseAtual * 2);  // Médio
            default -> 14 + (faseAtual * 2);  // Difícil
        };

        // setVivo(false) encerra o while(vivo) de cada thread antes do clear().
        // Sem isso, threads antigas continuariam rodando em background e
        // disparando de posições inválidas ("tiro fantasma").
        for (TanqueInimigo inimigo : listaInimigos) inimigo.setVivo(false);

        projeteis.clear();
        listaInimigos.clear();
        powerUps.clear();
        explosoes.clear();

        gerenciadorMapa.carregarFase(faseAtual);

        // Reposiciona jogador com invencibilidade temporária de 3 segundos
        int[] coordJogador = gerenciadorMapa.encontrarEspacoVazio(false);
        if (jogador != null) {
            jogador.posicionarEmSeguranca(coordJogador[0], coordJogador[1]);
            jogador.setVivo(true);
            jogador.hp = HP_MAXIMO;
        }

        // Spawna inimigos com tipo variando por dificuldade
        for (int i = 0; i < totalInimigosFase; i++) {
            int[] coordInimigo = encontrarLugarAleatorio();
            int tipo = switch (dificuldade) {
                case 0  -> 0;                  // Fácil: só normais (cinza)
                case 1  -> random.nextInt(2);  // Médio: normais e rápidos (verde)
                default -> random.nextInt(3);  // Difícil: todos (cinza, verde, vermelho)
            };
            listaInimigos.add(new TanqueInimigo(this, coordInimigo[0], coordInimigo[1], tipo));
        }
    }

    // =========================================================================
    // GAME LOOP — ATUALIZAÇÃO
    // =========================================================================

    /**
     * Máquina de estados principal — chamada a cada frame pelo game loop.
     * Roteia para o comportamento correto baseado no estado atual do jogo.
     */
    public void atualizar() {
        // Alternância ESC com trava para evitar toggle rápido involuntário
        if (teclaH.esc && estadoJogo == ESTADO_JOGANDO && !escTrava) {
            estadoJogo = ESTADO_PAUSADO;
            escTrava   = true;
            teclaH.limparTeclas();
        } else if (!teclaH.esc) {
            escTrava = false;
        }

        if (estadoJogo == ESTADO_JOGANDO) {
            atualizarMundo();

        } else if (estadoJogo == ESTADO_PAUSADO) {
            atualizarMenuPausa();

        } else if (estadoJogo == ESTADO_VITORIA) {
            if (teclaH.esc) {
                reiniciarJogoCompleto();
                estadoJogo = ESTADO_JOGANDO;
                teclaH.esc = false;
            }

        } else if (estadoJogo == ESTADO_GAMEOVER && !scoreSalvo) {
            // Score salvo assim que entra em game over — garante que só ocorre uma vez
            GerenciadorRanking.adicionarScore(nomeJogador, pontuacaoTotal);
            scoreSalvo = true;

        } else if ((estadoJogo == ESTADO_MORTE || estadoJogo == ESTADO_GAMEOVER) && teclaH.enter) {
            teclaH.enter = false;
            if (estadoJogo == ESTADO_MORTE) reiniciarFaseAtual();
            else                            reiniciarJogoCompleto();
            estadoJogo = ESTADO_JOGANDO;
        }
    }

    /**
     * Atualiza entidades e física durante ESTADO_JOGANDO.
     * TanqueInimigo não é atualizado aqui — cada um tem sua própria thread.
     */
    private void atualizarMundo() {
        if (jogador != null) jogador.atualizar();

        for (PowerUp  powerUp  : powerUps)  powerUp.atualizar();
        for (Explosao explosao : explosoes) explosao.atualizar();

        powerUps.removeIf(pu -> !pu.ativo);
        explosoes.removeIf(ex -> !ex.ativa);

        verificarColisoes();
    }

    // =========================================================================
    // DETECÇÃO DE COLISÕES
    // =========================================================================

    /**
     * Verifica todas as colisões relevantes a cada frame.
     *
     * A ORDEM IMPORTA:
     *   1. Base (Águia) — verificada ANTES do isAtivo() porque Projetil pode
     *      ser desativado por verificarColisaoCenario() ao tocar o tile 2, mas
     *      só o GamePanel pode acionar o Game Over. Checar isAtivo() primeiro
     *      faria o projétil ser ignorado antes de verificar a base.
     *   2. Inimigos  — projétil do jogador mata inimigo, spawna explosão e power-up.
     *   3. Jogador   — projétil de inimigo reduz HP ou mata o jogador.
     *   4. Power-ups — colisão por sobreposição, sem projétil envolvido.
     */
    public void verificarColisoes() {
        if (estadoJogo != ESTADO_JOGANDO) return;
        if (projeteis == null || jogador == null) return;

        for (Projetil projetil : projeteis) {

            // 1. COLISÃO COM A BASE (Águia) — game over se destruída
            Rectangle baseHitbox = new Rectangle(6 * TAMANHO_BLOCO, 12 * TAMANHO_BLOCO, TAMANHO_BLOCO, TAMANHO_BLOCO);
            if (projetil.hitbox.intersects(baseHitbox)) {
                projetil.setAtivo(false);
                jogador.vidas--;
                estadoJogo = (jogador.vidas > 0) ? ESTADO_MORTE : ESTADO_GAMEOVER;
                return;
            }

            if (!projetil.isAtivo()) continue; // Projéteis já desativados ignorados daqui em diante

            // 2. COLISÃO PROJÉTIL → INIMIGO
            for (TanqueInimigo inimigo : listaInimigos) {
                if (inimigo.isVivo() && projetil.hitbox.intersects(inimigo.hitbox)) {

                    if (projetil.origem == jogador) {
                        // Projétil do jogador acertou um inimigo
                        projetil.setAtivo(false);
                        inimigo.setVivo(false);
                        explosoes.add(new Explosao(this, inimigo.getX(), inimigo.getY()));
                        inimigosMortos++;
                        adicionarPontos(PONTOS_POR_KILL);

                        // 30% de chance de dropar um power-up de vida extra
                        if (random.nextInt(100) < 30) {
                            powerUps.add(new PowerUp(this, inimigo.getX(), inimigo.getY()));
                        }

                        if (inimigosMortos >= totalInimigosFase) {
                            concluirMissao();
                            return;
                        }

                    } else if (projetil.origem != inimigo && projetil.origem != jogador) {
                        // Fogo amigo: projétil de inimigo A atingiu inimigo B — cancela projétil
                        projetil.setAtivo(false);
                    }
                }
            }

            // 3. COLISÃO PROJÉTIL → JOGADOR
            if (jogador.isVivo()
                    && projetil.hitbox.intersects(jogador.hitbox)
                    && projetil.origem != jogador) {

                projetil.setAtivo(false);

                if (!jogador.isInvencivel()) {
                    jogador.hp -= DANO_TIRO;
                    if (jogador.hp <= 1) {
                        jogador.vidas--;
                        estadoJogo = (jogador.vidas > 0) ? ESTADO_MORTE : ESTADO_GAMEOVER;
                        return;
                    }
                }
            }
        }

        // 4. COLISÃO JOGADOR → POWER-UP (vida extra)
        if (jogador.isVivo()) {
            for (PowerUp powerUp : powerUps) {
                if (powerUp.ativo && jogador.getHitbox().intersects(powerUp.getHitbox())) {
                    powerUp.ativo = false;
                    jogador.vidas++;
                    adicionarPontos(500);
                }
            }
        }

        // Remove projéteis inativos de uma só vez — CopyOnWriteArrayList é thread-safe
        projeteis.removeIf(p -> !p.isAtivo());
    }

    // =========================================================================
    // RENDERIZAÇÃO
    // =========================================================================

    /**
     * Renderiza o mundo em ordem de camadas (Painter's Algorithm):
     *   1. Camada baixo : cenário de fundo + tiles de chão e paredes
     *   2. Entidades    : jogador, inimigos, projéteis, power-ups, explosões
     *   3. Camada topo  : grama — desenhada por cima dos tanques
     *   4. HUD          : painel lateral com informações da partida
     *   5. Overlays     : pausa, morte, game over, vitória
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        if (gerenciadorMapa == null) return;

        // Camadas do mundo
        gerenciadorMapa.desenharCamadaBaixo(g2);
        if (jogador != null && jogador.isVivo()) jogador.desenhar(g2);
        for (TanqueInimigo inimigo  : listaInimigos) if (inimigo.isVivo())                   inimigo.desenhar(g2);
        for (Projetil      projetil : projeteis)     if (projetil != null && projetil.isAtivo()) projetil.desenhar(g2);
        for (PowerUp       powerUp  : powerUps)      powerUp.desenhar(g2);
        for (Explosao      explosao : explosoes)     explosao.desenhar(g2);
        gerenciadorMapa.desenharCamadaTopo(g2);

        // Interface e overlays
        desenharHUD(g2);
        if (estadoJogo == ESTADO_PAUSADO)  desenharTelaPausa(g2);
        if (estadoJogo == ESTADO_MORTE)    desenharOverlayDerrota(g2, imgAvisoMorte, "ENTER para Tentar de Novo");
        if (estadoJogo == ESTADO_GAMEOVER) desenharOverlayDerrota(g2, imgGameOver,   "Pressione ENTER para Reiniciar Jogo");
        if (estadoJogo == ESTADO_VITORIA)  desenharOverlayVitoria(g2, imgWon,        "PARABÉNS! ESC para Reiniciar");

        g2.dispose();
    }

    /** Painel lateral com nome, score, dificuldade, barra de HP, vidas, inimigos restantes e fase. */
    public void desenharHUD(Graphics2D g2) {
        // Fundo e separador do HUD
        g2.setColor(Color.LIGHT_GRAY);
        g2.fillRect(LARGURA_MAPA, 0, LARGURA_HUD, ALTURA_TELA);
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(4));
        g2.drawLine(LARGURA_MAPA, 0, LARGURA_MAPA, ALTURA_TELA);

        int xHUD = LARGURA_MAPA + 20;
        String[] nomesDificuldade = {"FÁCIL", "MÉDIO", "DIFÍCIL"};

        // Informações textuais
        g2.setFont(new Font("Monospaced", Font.BOLD, 16));
        g2.setColor(Color.BLACK);
        g2.drawString("PILOTO: " + nomeJogador,                                           xHUD, 40);
        g2.drawString("SCORE: " + String.format("%06d", pontuacaoTotal + pontuacaoFase),  xHUD, 60);
        g2.drawString("MODO: " + nomesDificuldade[dificuldade],                            xHUD, 80);

        // Barra de blindagem (HP)
        g2.setFont(new Font("Arial", Font.BOLD, 14));
        g2.setColor(Color.BLACK);
        g2.drawString("BLINDAGEM", xHUD, 128);
        g2.setColor(Color.RED);   g2.fillRect(xHUD, 140, 150, 15);                    // Fundo vermelho = dano
        g2.setColor(Color.GREEN); g2.fillRect(xHUD, 140, (int)(jogador.hp * 1.5), 15); // HP atual em verde
        g2.setColor(Color.BLACK); g2.drawRect(xHUD, 140, 150, 15);                    // Borda da barra

        // Contadores
        g2.setFont(new Font("Arial", Font.BOLD, 20));
        g2.setColor(Color.BLACK);
        g2.drawString("VIDAS: x"    + (jogador != null ? jogador.vidas : 0),  xHUD, 222);
        g2.drawString("INIMIGOS: x" + (totalInimigosFase - inimigosMortos),   xHUD, 280);
        g2.drawString("FASE "       + faseAtual,                               xHUD, ALTURA_TELA - 30);
    }

    private void desenharOverlayVitoria(Graphics2D g2, BufferedImage imagem, String mensagem) {
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRect(0, 0, LARGURA_TELA, ALTURA_TELA);

        if (imagem != null) {
            int imgX = (LARGURA_TELA / 2) - (imagem.getWidth() / 2);
            int imgY = (ALTURA_TELA / 2) - (imagem.getHeight() / 2) - 40;
            g2.drawImage(imagem, imgX, imgY, null);
        }

        g2.setColor(Color.YELLOW);
        g2.setFont(new Font("Arial", Font.BOLD, 25));
        int msgX = (LARGURA_TELA / 2) - (g2.getFontMetrics().stringWidth(mensagem) / 2);
        g2.drawString(mensagem, msgX, (ALTURA_TELA / 2) + 120);
    }

    private void desenharOverlayDerrota(Graphics2D g2, BufferedImage imagem, String mensagem) {
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(0, 0, LARGURA_TELA, ALTURA_TELA);

        if (imagem != null) {
            int imgX = (LARGURA_TELA / 2) - (imagem.getWidth() / 2);
            int imgY = (ALTURA_TELA / 2) - (imagem.getHeight() / 2) - 20;
            g2.drawImage(imagem, imgX, imgY, null);
        }

        g2.setColor(Color.WHITE);
        int msgX = (LARGURA_TELA / 2) - (g2.getFontMetrics().stringWidth(mensagem) / 2);
        g2.drawString(mensagem, msgX, (ALTURA_TELA / 2) + 100);
    }

    private void desenharTelaPausa(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRect(0, 0, LARGURA_MAPA, ALTURA_TELA);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 40));
        g2.drawString("PAUSADO", (LARGURA_MAPA / 2) - 80, ALTURA_TELA / 2 - 80);

        g2.setFont(new Font("Arial", Font.BOLD, 22));
        String[] opcoes = {"   CONTINUAR", "   REINICIAR FASE", "   NOVO JOGO", "   VOLTAR AO MENU"};

        for (int i = 0; i < opcoes.length; i++) {
            int x = (LARGURA_MAPA / 2) - (g2.getFontMetrics().stringWidth(opcoes[i]) / 2);
            int y = (ALTURA_TELA / 2) - 20 + (i * 40);

            if (i == comandoNum) {
                g2.setColor(Color.YELLOW);
                g2.drawString(">", x - 30, y); // Cursor indicando opção selecionada
            } else {
                g2.setColor(Color.WHITE);
            }
            g2.drawString(opcoes[i], x, y);
        }
    }

    /** Navega o menu de pausa com cima/baixo e confirma com ENTER. */
    private void atualizarMenuPausa() {
        if (teclaH.cima)  { comandoNum = (comandoNum <= 0) ? 3 : comandoNum - 1; teclaH.cima  = false; }
        if (teclaH.baixo) { comandoNum = (comandoNum >= 3) ? 0 : comandoNum + 1; teclaH.baixo = false; }

        if (teclaH.enter) {
            teclaH.enter = false;
            switch (comandoNum) {
                case 0 -> estadoJogo = ESTADO_JOGANDO;
                case 1 -> { reiniciarFaseAtual();    estadoJogo = ESTADO_JOGANDO; }
                case 2 -> { reiniciarJogoCompleto(); estadoJogo = ESTADO_JOGANDO; }
                case 3 -> { rodando = false; main.irParaMenuPrincipal(); }
            }
        }
    }

    // =========================================================================
    // AUXILIARES DE SPAWN
    // =========================================================================

    /**
     * Encontra coordenadas de pixel seguras para spawnar um inimigo.
     * Tenta 500 posições aleatórias antes de fazer varredura linear (fallback).
     * Exclui as últimas 3 linhas do mapa (área da base do jogador).
     */
    private int[] encontrarLugarAleatorio() {
        // Tentativa aleatória — rápida na maioria dos casos
        for (int tentativa = 0; tentativa < 500; tentativa++) {
            int col = random.nextInt(COLUNAS);
            int lin = random.nextInt(LINHAS - 3); // Exclui área da base
            if (isLocalSeguro(col, lin)) {
                return new int[]{col * TAMANHO_BLOCO, lin * TAMANHO_BLOCO};
            }
        }

        // Fallback: varredura linear garante encontrar um espaço se ele existir
        for (int lin = 0; lin < LINHAS - 3; lin++) {
            for (int col = 0; col < COLUNAS; col++) {
                if (isLocalSeguro(col, lin)) {
                    return new int[]{col * TAMANHO_BLOCO, lin * TAMANHO_BLOCO};
                }
            }
        }

        return new int[]{0, 0}; // Último recurso — mapa completamente bloqueado
    }

    /**
     * Verifica se um tile é passável E tem pelo menos uma saída livre.
     * Evita spawnar inimigos em becos sem saída.
     */
    private boolean isLocalSeguro(int col, int lin) {
        int tile = gerenciadorMapa.mapaTileNum[col][lin];
        if (tile != 0 && tile != 3) return false; // Só vazio (0) e grama (3) são passáveis

        int saidasLivres = 0;
        if (lin > 0           && isTilePassavel(col, lin - 1)) saidasLivres++;
        if (lin < LINHAS - 1  && isTilePassavel(col, lin + 1)) saidasLivres++;
        if (col > 0           && isTilePassavel(col - 1, lin)) saidasLivres++;
        if (col < COLUNAS - 1 && isTilePassavel(col + 1, lin)) saidasLivres++;

        return saidasLivres >= 1;
    }

    /** Retorna true se o tile permite movimentação de tanques (vazio ou grama). */
    private boolean isTilePassavel(int col, int lin) {
        int tile = gerenciadorMapa.mapaTileNum[col][lin];
        return tile == 0 || tile == 3;
    }

    // =========================================================================
    // PONTUAÇÃO E BÔNUS
    // =========================================================================

    public void adicionarPontos(int pontos) {
        this.pontuacaoFase += pontos;
    }

    /**
     * Calcula o bônus de fim de fase e acumula na pontuação total.
     *
     * Fórmula: pontuacaoFase × (1 + vidas + hp)
     * Exemplo: 500pts com 3 vidas e 75 HP → bônus = 500 × 79 = 39.500
     */
    public void calcularBonusFimDeFase() {
        int bonus = pontuacaoFase
                + (pontuacaoFase * jogador.vidas)
                + (pontuacaoFase * jogador.hp);
        pontuacaoTotal += bonus;
        pontuacaoFase   = 0;
    }

    // =========================================================================
    // MOTOR DO GAME LOOP
    // =========================================================================

    /**
     * Inicia a thread do game loop.
     * O if (gameThread == null) protege contra dupla inicialização —
     * Main.iniciarJogo() também chama este método após montar a janela,
     * mas a segunda chamada é ignorada com segurança.
     */
    public void iniciarThreadJogo() {
        if (gameThread == null) {
            rodando    = true;
            gameThread = new Thread(this);
            gameThread.start();
        }
    }

    /**
     * Game loop com fixed timestep a 60 FPS.
     * Calcula o tempo restante após atualizar() e repaint() e dorme
     * esse intervalo para manter o framerate constante sem busy-wait.
     */
    @Override
    public void run() {
        double intervaloPorFrame = NANO_POR_SEGUNDO / FPS;
        double proximoFrame      = System.nanoTime() + intervaloPorFrame;

        while (rodando) {
            atualizar();
            repaint();

            // Dorme o tempo restante até o próximo frame
            double tempoRestanteMs = (proximoFrame - System.nanoTime()) / 1_000_000.0;
            try {
                Thread.sleep((long) Math.max(0, tempoRestanteMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restaura flag de interrupção — boa prática
            }
            proximoFrame += intervaloPorFrame;
        }
    }

    // =========================================================================
    // MÉTODOS PÚBLICOS DE SUPORTE
    // =========================================================================

    /**
     * Adiciona um projétil de forma thread-safe (synchronized).
     * Chamado por TanqueJogador e TanqueInimigo em suas próprias threads.
     */
    public synchronized void adicionarProjetil(Projetil projetil) {
        if (projeteis != null) projeteis.add(projetil);
    }

    /** Carrega imagens de overlay (morte, game over, vitória). */
    private void carregarImagensExtras() {
        try {
            imgAvisoMorte = ImageUtils.transformarTransparente(
                ImageIO.read(getClass().getResourceAsStream("/imagens/avisoMorte.jpg")), Color.WHITE);
            imgGameOver   = ImageUtils.transformarTransparente(
                ImageIO.read(getClass().getResourceAsStream("/imagens/game_over.bmp")), Color.WHITE);
            imgWon        = ImageUtils.transformarTransparente(
                ImageIO.read(getClass().getResourceAsStream("/imagens/won.bmp")), Color.BLACK);
        } catch (Exception e) {
            System.err.println("Aviso: assets de overlay não encontrados — " + e.getMessage());
        }
    }
}