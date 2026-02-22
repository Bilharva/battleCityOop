package com.battlecity.entities;

import com.battlecity.main.GamePanel;
import java.awt.*;

/**
 * Projétil disparado por tanques. Cada instância roda em thread própria.
 *
 * Divisão de responsabilidade de colisão:
 *   - {@code Projetil}: detecta colisão com tiles do cenário (paredes, aço, base).
 *   - {@code GamePanel}: detecta colisão com entidades (inimigos, jogador).
 *
 * Por que thread própria?
 * O professor exige uma thread por projétil. Isso permite que cada bala
 * se mova de forma independente e contínua sem bloquear o game loop principal.
 *
 * Nota sobre o tile 2 (Base/Águia): ao tocar a base, o projétil apenas para.
 * O Game Over é responsabilidade exclusiva de {@code GamePanel.verificarColisoes()}.
 */
public class Projetil extends Entidade implements Movivel, Runnable {

    private final GamePanel gp;

    /** volatile: garante visibilidade imediata entre threads sem necessidade de synchronized. */
    public volatile boolean ativo = true;

    /** Referência a quem disparou — usada para evitar fogo amigo nas colisões. */
    public final Entidade origem;

    // =========================================================================
    // CONSTRUTOR
    // =========================================================================

    public Projetil(GamePanel gp, int x, int y, String direcao, Entidade origem) {
        super(x, y, 7); // Velocidade 7px por tick — maior que os tanques (2–4px)
        this.gp      = gp;
        this.direcao = direcao;
        this.origem  = origem;
        this.hitbox  = new Rectangle(x, y, 10, 10); // Hitbox pequena para precisão de colisão

        new Thread(this).start();
    }

    // =========================================================================
    // THREAD — LOOP DE MOVIMENTO
    // =========================================================================

    @Override
    public void run() {
        while (ativo && gp.rodando) {
            if (gp.estadoJogo == gp.ESTADO_JOGANDO) {
                mover();
            }
            try {
                Thread.sleep(10); // Intervalo menor que tanques (16ms) = projétil mais rápido
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        ativo = false; // Garante estado consistente ao sair do loop por qualquer motivo
    }

    // =========================================================================
    // MOVIMENTO E COLISÃO COM CENÁRIO
    // =========================================================================

    @Override
    public void mover() {
        switch (direcao) {
            case "cima"     -> y -= velocidade;
            case "baixo"    -> y += velocidade;
            case "esquerda" -> x -= velocidade;
            case "direita"  -> x += velocidade;
        }

        hitbox.x = x;
        hitbox.y = y;

        verificarColisaoCenario();

        if (x < 0 || x > gp.LARGURA_TELA || y < 0 || y > gp.ALTURA_TELA) ativo = false;
    }

    /**
     * Verifica colisão com tiles do mapa na posição atual do projétil.
     *
     * Tile 1 (tijolo) → destrói o tile e desativa o projétil.
     * Tile 4 (aço)    → projétil para; tile é indestrutível.
     * Tile 2 (base)   → projétil para; Game Over tratado pelo GamePanel.
     */
    private void verificarColisaoCenario() {
        int col = (x + 5) / gp.TAMANHO_BLOCO;
        int lin = (y + 5) / gp.TAMANHO_BLOCO;

        if (col < 0 || col >= gp.COLUNAS || lin < 0 || lin >= gp.LINHAS) return;

        int tileID = gp.gerenciadorMapa.mapaTileNum[col][lin];

        switch (tileID) {
            case 1 -> { gp.gerenciadorMapa.mapaTileNum[col][lin] = 0; ativo = false; } // Tijolo destruído
            case 4 -> ativo = false; // Aço: bloqueia sem destruir
            case 2 -> ativo = false; // Base: GamePanel aciona Game Over
        }
    }

    // =========================================================================
    // GETTERS E RENDERIZAÇÃO
    // =========================================================================

    public boolean isAtivo()                  { return ativo; }
    public void    setAtivo(boolean ativo)    { this.ativo = ativo; }

    /** Movimento delegado à thread — este método existe apenas para cumprir o contrato de Entidade. */
    @Override public void atualizar() {}

    @Override
    public void desenhar(Graphics2D g2) {
        if (!ativo) return;
        g2.setColor(Color.YELLOW);
        g2.fillOval(x, y, 10, 10);
    }
}