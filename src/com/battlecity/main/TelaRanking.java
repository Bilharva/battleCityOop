package com.battlecity.main;

import java.awt.*;
import java.util.List;
import javax.swing.*;

public class TelaRanking extends JPanel {

    Main main;
    private final List<Score> lista; // Carregado uma vez no construtor — evita I/O a cada repaint

    public TelaRanking(Main main) {
        this.main  = main;
        this.lista = GerenciadorRanking.carregarRanking(); // Leitura de arquivo feita aqui, uma única vez
        this.setBackground(Color.BLACK);
        this.setLayout(null);
        this.setPreferredSize(new Dimension(720, 520));

        // Título
        JLabel titulo = new JLabel("TOP 10 JOGADORES");
        titulo.setFont(new Font("Monospaced", Font.BOLD, 40));
        titulo.setForeground(Color.YELLOW);
        titulo.setBounds(150, 30, 500, 50);
        titulo.setHorizontalAlignment(SwingConstants.CENTER);
        this.add(titulo);

        // Botão Voltar
        JButton btnVoltar = new JButton("VOLTAR AO MENU");
        btnVoltar.setBounds(260, 450, 200, 40);
        btnVoltar.setBackground(Color.DARK_GRAY);
        btnVoltar.setForeground(Color.WHITE);
        btnVoltar.setFont(new Font("Arial", Font.BOLD, 15));
        btnVoltar.addActionListener(e -> main.irParaMenuPrincipal());
        this.add(btnVoltar);
    }

    // Usamos paintComponent para desenhar a lista bonita
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        
        // 'lista' já foi carregada no construtor — sem I/O aqui
        
        g2.setFont(new Font("Monospaced", Font.BOLD, 20));
        int y = 120;
        int i = 1;

        // Cabeçalho
        g2.setColor(Color.GRAY);
        g2.drawString("POS   NOME                PONTOS", 150, 100);
        g2.drawLine(150, 105, 570, 105);

        for (Score s : lista) {
            if (i == 1) g2.setColor(Color.ORANGE); // 1º Lugar Ouro
            else if (i == 2) g2.setColor(Color.LIGHT_GRAY); // 2º Prata
            else if (i == 3) g2.setColor(new Color(205, 127, 50)); // 3º Bronze
            else g2.setColor(Color.WHITE);

            String linha = String.format("%-5d %-20s %06d", i, s.getNome(), s.getPontos());
            g2.drawString(linha, 150, y);
            y += 30;
            i++;
        }
        
        if (lista.isEmpty()) {
            g2.setColor(Color.WHITE);
            g2.drawString("NENHUM REGISTRO ENCONTRADO", 200, 200);
        }
    }
    
   
}