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
    private BufferedImage hudImagemVida;

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
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        final int X      = LARGURA_MAPA;
        final int W      = LARGURA_HUD;
        final int H      = ALTURA_TELA;
        final int PAD    = 14;
        final int CX     = X + PAD;                  // X base do conteúdo
        final int CW     = W - PAD * 2;              // Largura útil do conteúdo

        // ── Fundo escuro ─────────────────────────────────────────────────────
        g2.setColor(new Color(18, 20, 28));
        g2.fillRect(X, 0, W, H);

        // Borda lateral esquerda laranja
        g2.setColor(new Color(220, 120, 20));
        g2.setStroke(new BasicStroke(3));
        g2.drawLine(X + 2, 0, X + 2, H);
        g2.setStroke(new BasicStroke(1));

        // ── Helper: divisor fino ─────────────────────────────────────────────
        // (usado inline abaixo com g2.fillRect)

        // ── SEÇÃO: Cabeçalho (jogador + score + modo) ─────────────────────
        int y = 18;

        // Label "JOGADOR"
        g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g2.setColor(new Color(140, 140, 160));
        g2.drawString("JOGADOR", CX, y);
        y += 16;

        // Nome do jogador em destaque
        g2.setFont(new Font("Monospaced", Font.BOLD, 15));
        g2.setColor(new Color(255, 200, 50));
        String nomeExibido = nomeJogador.length() > 11 ? nomeJogador.substring(0, 11) : nomeJogador;
        g2.drawString(nomeExibido, CX, y);
        y += 18;

        // Score
        g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g2.setColor(new Color(140, 140, 160));
        g2.drawString("PONTUAÇÃO", CX, y);
        y += 15;
        g2.setFont(new Font("Monospaced", Font.BOLD, 16));
        g2.setColor(Color.WHITE);
        g2.drawString(String.format("%07d", pontuacaoTotal + pontuacaoFase), CX, y);
        y += 16;

        // Modo de dificuldade
        String[] nomesDificuldade = {"FÁCIL", "MÉDIO", "DIFÍCIL"};
        Color[]  coresDificuldade = {new Color(80, 200, 80), new Color(240, 180, 30), new Color(220, 60, 60)};
        g2.setFont(new Font("Monospaced", Font.BOLD, 11));
        g2.setColor(coresDificuldade[dificuldade]);
        g2.drawString("● " + nomesDificuldade[dificuldade], CX, y);
        y += 14;

        // Divisor
        g2.setColor(new Color(50, 55, 70));
        g2.fillRect(CX, y, CW, 1);
        y += 10;

        // ── SEÇÃO: Blindagem (HP) ─────────────────────────────────────────
        g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g2.setColor(new Color(140, 140, 160));
        g2.drawString("BLINDAGEM", CX, y);
        y += 12;

        // Barra de HP com cantos arredondados
        int hpAtual   = (jogador != null) ? jogador.hp : 0;
        int barraLarg = CW;
        int barraAlt  = 12;
        int hpLarg    = (int)((hpAtual / (float) HP_MAXIMO) * barraLarg);
        // Fundo vermelho escuro (dano)
        g2.setColor(new Color(100, 20, 20));
        g2.fillRoundRect(CX, y, barraLarg, barraAlt, 6, 6);
        // HP atual: verde → amarelo → vermelho conforme percentual
        float pct = hpAtual / (float) HP_MAXIMO;
        Color corHP = pct > 0.5f
            ? new Color(50, 200, 50)
            : pct > 0.25f
                ? new Color(220, 180, 30)
                : new Color(220, 50, 50);
        if (hpLarg > 0) {
            g2.setColor(corHP);
            g2.fillRoundRect(CX, y, hpLarg, barraAlt, 6, 6);
        }
        // Borda fina
        g2.setColor(new Color(80, 85, 100));
        g2.drawRoundRect(CX, y, barraLarg, barraAlt, 6, 6);
        // Percentual dentro da barra
        g2.setFont(new Font("Monospaced", Font.BOLD, 9));
        g2.setColor(Color.WHITE);
        String pctStr = (int)(pct * 100) + "%";
        int pctX = CX + barraLarg / 2 - g2.getFontMetrics().stringWidth(pctStr) / 2;
        g2.drawString(pctStr, pctX, y + barraAlt - 2);
        y += barraAlt + 12;

        // Divisor
        g2.setColor(new Color(50, 55, 70));
        g2.fillRect(CX, y, CW, 1);
        y += 12;

        // ── SEÇÃO: Vidas ──────────────────────────────────────────────────
        int vidas = (jogador != null) ? jogador.vidas : 0;

        g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g2.setColor(new Color(140, 140, 160));
        g2.drawString("VIDAS", CX, y);
        y += 14;

        // Ícones de vida (até 5 ícones; se tiver mais, mostra "xN" ao lado)
        int iconeSize  = 24;
        int iconeGap   = 4;
        int maxIcones  = Math.min(vidas, 5);
        int iconeY     = y;

        for (int i = 0; i < maxIcones; i++) {
            if (hudImagemVida != null) {
                g2.drawImage(hudImagemVida, CX + i * (iconeSize + iconeGap), iconeY, iconeSize, iconeSize, null);
            } else {
                // Fallback: coração vermelho
                g2.setFont(new Font("Arial", Font.BOLD, 20));
                g2.setColor(new Color(220, 50, 50));
                g2.drawString("♥", CX + i * (iconeSize + iconeGap), iconeY + iconeSize - 2);
            }
        }
        // Número de vidas ao lado dos ícones (sempre visível)
        g2.setFont(new Font("Monospaced", Font.BOLD, 18));
        g2.setColor(Color.WHITE);
        int numX = CX + maxIcones * (iconeSize + iconeGap) + 4;
        g2.drawString("x" + vidas, numX, iconeY + iconeSize - 4);
        y += iconeSize + 12;

        // Divisor
        g2.setColor(new Color(50, 55, 70));
        g2.fillRect(CX, y, CW, 1);
        y += 12;

        // ── SEÇÃO: Inimigos ───────────────────────────────────────────────
        int inimigoRestantes = totalInimigosFase - inimigosMortos;

        g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g2.setColor(new Color(140, 140, 160));
        g2.drawString("INIMIGOS", CX, y);
        y += 14;

        // Contador grande em vermelho
        g2.setFont(new Font("Monospaced", Font.BOLD, 32));
        g2.setColor(new Color(220, 60, 60));
        g2.drawString("x" + inimigoRestantes, CX, y + 28);

        // Mini barra de progresso da fase (inimigos mortos / total)
        int progY     = y + 36;
        int progLarg  = CW;
        int progAlt   = 6;
        float progPct = totalInimigosFase > 0 ? inimigosMortos / (float) totalInimigosFase : 0f;
        g2.setColor(new Color(40, 45, 60));
        g2.fillRoundRect(CX, progY, progLarg, progAlt, 4, 4);
        g2.setColor(new Color(80, 180, 80));
        g2.fillRoundRect(CX, progY, (int)(progLarg * progPct), progAlt, 4, 4);
        g2.setColor(new Color(80, 85, 100));
        g2.drawRoundRect(CX, progY, progLarg, progAlt, 4, 4);
        y += 50;

        // Divisor
        g2.setColor(new Color(50, 55, 70));
        g2.fillRect(CX, y, CW, 1);

        // ── SEÇÃO: Fase (rodapé) ──────────────────────────────────────────
        int rodapeY = H - 44;

        // Caixa de destaque da fase
        g2.setColor(new Color(30, 35, 50));
        g2.fillRoundRect(CX, rodapeY, CW, 34, 8, 8);
        g2.setColor(new Color(220, 120, 20));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(CX, rodapeY, CW, 34, 8, 8);
        g2.setStroke(new BasicStroke(1));

        g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g2.setColor(new Color(140, 140, 160));
        g2.drawString("FASE", CX + CW / 2 - 12, rodapeY + 13);

        g2.setFont(new Font("Monospaced", Font.BOLD, 16));
        g2.setColor(new Color(255, 200, 50));
        String faseStr = faseAtual + " / " + MAX_FASES;
        int faseStrW = g2.getFontMetrics().stringWidth(faseStr);
        g2.drawString(faseStr, CX + CW / 2 - faseStrW / 2, rodapeY + 29);
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
        try {
            var stream = getClass().getResourceAsStream("/imagens/vida.png");
            if (stream != null)
                hudImagemVida = ImageUtils.transformarTransparente(ImageIO.read(stream), Color.WHITE);
        } catch (Exception e) {
            System.err.println("Aviso: vida.png não encontrada no HUD.");
        }
    }
}