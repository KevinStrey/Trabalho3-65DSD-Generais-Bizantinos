// Arquivo: simulador/ProcessoGUI.java
package simulador;

import java.awt.BorderLayout;
import java.awt.Font;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;

public class ProcessoGUI {

    private JTextArea logArea;
    private JFrame frame;

    /**
     * Classe interna simples para guardar os resultados do diálogo.
     */
    private static class Configuracao {
        final int meuId;
        final int comandanteId;
        final boolean isTraidor;

        Configuracao(int meuId, int comandanteId, boolean isTraidor) {
            this.meuId = meuId;
            this.comandanteId = comandanteId;
            this.isTraidor = isTraidor;
        }
    }

    /**
     * Construtor principal: Inicia a GUI de log e o processo.
     */
    public ProcessoGUI(Configuracao config, Map<Integer, String[]> mapaRede) {
        // 1. Cria a janela principal de log
        frame = new JFrame("General " + config.meuId + " (Cmd: " + config.comandanteId + " | Traidor: " + config.isTraidor + ")");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.setVisible(true);

        // 2. Cria e inicia o Processo em uma nova thread
        Processo p = new Processo(config.meuId, config.comandanteId, config.isTraidor, mapaRede, logArea);
        new Thread(p::iniciar).start();
    }

    /**
     * Mostra um diálogo modal para o usuário inserir a configuração.
     */
    private static Configuracao mostrarDialogoConfiguracao() {
        // Cria os componentes da UI
        JSpinner idSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 10, 1));
        JSpinner cmdSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 10, 1));
        JCheckBox traidorCheck = new JCheckBox("É Traidor?");

        // Coloca em um painel
        JPanel panel = new JPanel();
        panel.setLayout(new java.awt.GridLayout(0, 1, 5, 5));
        panel.add(new javax.swing.JLabel("Meu ID:"));
        panel.add(idSpinner);
        panel.add(new javax.swing.JLabel("ID do Comandante:"));
        panel.add(cmdSpinner);
        panel.add(traidorCheck);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Mostra o diálogo
        int result = JOptionPane.showConfirmDialog(null, panel, "Configurar Processo", 
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            // Se o usuário clicou OK, retorna a configuração
            return new Configuracao(
                (Integer) idSpinner.getValue(),
                (Integer) cmdSpinner.getValue(),
                traidorCheck.isSelected()
            );
        } else {
            // Se clicou Cancelar, retorna null
            return null;
        }
    }

    /**
     * Lê o arquivo de configuração e constrói o mapa de rede.
     */
    private static Map<Integer, String[]> lerConfiguracao(String nomeArquivo) throws FileNotFoundException {
        Map<Integer, String[]> mapaDeProcessos = new HashMap<>();
        File arquivo = new File(nomeArquivo);
        
        System.out.println("Lendo configuração de: " + arquivo.getAbsolutePath());
        
        try (Scanner scanner = new Scanner(arquivo)) {
            while (scanner.hasNextLine()) {
                String linha = scanner.nextLine().trim();
                if (linha.isEmpty() || linha.startsWith("#")) {
                    continue;
                }
                
                String[] partes = linha.split("\\s+");
                if (partes.length >= 3) {
                    int id = Integer.parseInt(partes[0]);
                    String ip = partes[1];
                    String porta = partes[2];
                    mapaDeProcessos.put(id, new String[]{ip, porta});
                }
            }
        }
        return mapaDeProcessos;
    }

    /**
     * Ponto de entrada principal do JAR.
     */
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 1. Pergunta a configuração ao usuário
        Configuracao config = mostrarDialogoConfiguracao();
        
        if (config == null) {
            System.out.println("Configuração cancelada pelo usuário. Encerrando.");
            System.exit(0);
        }

        try {
            // 2. Lê o config.txt
            Map<Integer, String[]> mapaRede = lerConfiguracao("config.txt");
            
            if (!mapaRede.containsKey(config.meuId)) {
                JOptionPane.showMessageDialog(null, 
                    "Erro: ID " + config.meuId + " não encontrado no config.txt!", 
                    "Erro de Configuração", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }

            // 3. Inicia a GUI de log e o processo
            new ProcessoGUI(config, mapaRede);
            
        } catch (FileNotFoundException e) {
            JOptionPane.showMessageDialog(null, 
                "Erro: Arquivo 'config.txt' não foi encontrado.\n" +
                "Certifique-se de que ele está na mesma pasta do .jar.", 
                "Erro de Arquivo", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}