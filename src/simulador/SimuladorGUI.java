package simulador;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner; // --- NOVO IMPORT ---
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel; // --- NOVO IMPORT ---
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

public class SimuladorGUI {

    // --- REMOVIDO: CONFIG_FILE e lerConfiguracao() ---
    
    // Configuração dos processos (agora gerada dinamicamente)
    private Map<Integer, String[]> processosConfig;
    
    // UI
    private final Map<Integer, JTextArea> logAreas = new HashMap<>();
    private final List<Processo> generais = new ArrayList<>();
    private final List<Thread> processThreads = new ArrayList<>();
    private final Object globalStepLock = new Object();

    // Componentes da UI
    private JFrame frame;
    private JComboBox<Integer> commanderComboBox;
    private List<JCheckBox> traitorCheckBoxes = new ArrayList<>();
    private JButton startButton;
    private JButton nextStepButton;
    
    // --- NOVOS COMPONENTES ---
    private JSpinner processCountSpinner; // Para escolher o número de generais
    private JButton setupButton; // Para aplicar a configuração
    private JPanel dynamicControlsPanel; // Painel para Comandante/Traidores
    private JPanel traitorsPanel; // Painel específico dos checkboxes
    private JPanel logsPanel; // Painel que contém as áreas de log
    private JScrollPane logsScrollPane; // ScrollPane para os logs
    // --- FIM DOS NOVOS COMPONENTES ---

    public SimuladorGUI() {
        // A leitura do config.txt foi removida.
        // A UI será criada vazia e preenchida após o setup.
        criarJanela();
    }
    
    private void criarJanela() {
        frame = new JFrame("Simulador Generais Bizantinos (Controlado por Passos)");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(10, 10));
        
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                encerrarSimulacao();
            }
        });

        // 1. Cria o painel de controle (agora com "Setup")
        frame.add(criarPainelDeControle(), BorderLayout.NORTH);

        // 2. Cria o painel de logs (inicialmente vazio)
        logsPanel = new JPanel(new GridLayout(1, 0, 10, 10)); // Layout inicial
        logsPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        logsScrollPane = new JScrollPane(logsPanel);
        logsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        logsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        
        frame.add(logsScrollPane, BorderLayout.CENTER);

        // 3. Exibe a janela
        frame.setMinimumSize(new Dimension(800, 500));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
    
    private JPanel criarPainelDeControle() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createTitledBorder("Configurações da Simulação"));

        // --- PAINEL DE SETUP (NOVO) ---
        JPanel setupPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        setupPanel.add(new JLabel("Número de Generais:"));
        
        // Spinner para 2 a 10 generais, com padrão 4
        SpinnerNumberModel model = new SpinnerNumberModel(4, 2, 10, 1);
        processCountSpinner = new JSpinner(model);
        processCountSpinner.setPreferredSize(new Dimension(60, 25));
        setupPanel.add(processCountSpinner);

        setupButton = new JButton("Configurar Simulação");
        setupButton.addActionListener(e -> configurarSimulacao());
        setupPanel.add(setupButton);
        
        mainPanel.add(setupPanel);
        
        // --- PAINEL DE CONTROLES DINÂMICOS (Comandante/Traidores) ---
        dynamicControlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        
        // Seletor do Comandante
        JPanel commanderBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        commanderBox.add(new JLabel("Comandante:"));
        commanderComboBox = new JComboBox<>();
        commanderComboBox.setPreferredSize(new Dimension(80, commanderComboBox.getPreferredSize().height));
        commanderBox.add(commanderComboBox);

        // Checkboxes dos Traidores
        JPanel traitorsBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        traitorsBox.add(new JLabel("Traidores:"));
        traitorsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)); // Sub-painel
        traitorsBox.add(traitorsPanel);
        
        dynamicControlsPanel.add(commanderBox);
        dynamicControlsPanel.add(traitorsBox);
        mainPanel.add(dynamicControlsPanel);

        // --- PAINEL DE BOTÕES DE AÇÃO ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        
        startButton = new JButton("Iniciar Simulação");
        startButton.setFont(new Font("System", Font.BOLD, 12));
        startButton.setBackground(new Color(0, 150, 0));
        startButton.setForeground(Color.WHITE);
        startButton.setOpaque(true);
        startButton.setBorderPainted(false);
        startButton.addActionListener(e -> iniciarSimulacao());

        nextStepButton = new JButton("Próxima Ação >>");
        nextStepButton.setFont(new Font("System", Font.BOLD, 12));
        nextStepButton.setBackground(new Color(0, 100, 200));
        nextStepButton.setForeground(Color.WHITE);
        nextStepButton.setOpaque(true);
        nextStepButton.setBorderPainted(false);
        nextStepButton.addActionListener(e -> executarProximoPasso());
        
        buttonPanel.add(startButton);
        buttonPanel.add(nextStepButton);
        mainPanel.add(buttonPanel);

        // --- ESTADO INICIAL ---
        // Desabilita tudo até que o "Configurar" seja pressionado
        setDynamicControlsEnabled(false);
        startButton.setEnabled(false);
        nextStepButton.setEnabled(false);
        
        return mainPanel;
    }

    /**
     * NOVO: Chamado pelo botão "Configurar Simulação".
     * Lê o JSpinner e recria toda a UI dinâmica.
     */
    private void configurarSimulacao() {
        int numGenerais = (Integer) processCountSpinner.getValue();

        // 1. Gera a configuração de rede em memória
        gerarConfiguracao(numGenerais);
        
        // 2. Atualiza o painel de logs (cria N áreas de texto)
        atualizarPainelDeLogs(numGenerais);
        
        // 3. Atualiza os controles (ComboBox e CheckBoxes)
        atualizarControlesDinamicos(numGenerais);

        // 4. Habilita/Desabilita os botões apropriados
        setDynamicControlsEnabled(true);
        startButton.setEnabled(true);
        nextStepButton.setEnabled(false);
    }
    
    /**
     * NOVO: Gera o Map 'processosConfig' em memória.
     */
    private void gerarConfiguracao(int numGenerais) {
        processosConfig = new HashMap<>();
        for (int i = 0; i < numGenerais; i++) {
            // Gera "ID -> [localhost, porta]" (ex: 8000, 8001, ...)
            processosConfig.put(i, new String[]{"localhost", String.valueOf(8000 + i)});
        }
    }

    /**
     * NOVO: Limpa e recria o painel de logs.
     */
    /**
     * NOVO: Limpa e recria o painel de logs.
     */
    private void atualizarPainelDeLogs(int numGenerais) {
        logsPanel.removeAll();
        logAreas.clear();
        
        // Define o layout de grade para o número correto de generais
        logsPanel.setLayout(new GridLayout(1, numGenerais, 10, 10));

        for (int id = 0; id < numGenerais; id++) {
            JPanel processLogBox = new JPanel(new BorderLayout(5, 5));
            JLabel title = new JLabel("Processo " + id, JLabel.CENTER);
            title.setFont(new Font("System", Font.BOLD, 14));
            
            JTextArea logArea = new JTextArea(20, 25);
            logArea.setEditable(false);
            logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            logArea.setBackground(Color.DARK_GRAY);
            logArea.setForeground(Color.LIGHT_GRAY);
            
            // --- ALTERAÇÃO AQUI ---
            // Habilita a quebra de linha automática
            logArea.setLineWrap(true);
            logArea.setWrapStyleWord(true);
            // --- FIM DA ALTERAÇÃO ---
            
            logAreas.put(id, logArea);
            processLogBox.add(title, BorderLayout.NORTH);
            
            // Adiciona o JScrollPane individual para o JTextArea
            // Isso permite a rolagem vertical se o texto for muito grande
            JScrollPane textAreaScrollPane = new JScrollPane(logArea);
            textAreaScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            textAreaScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER); // Desliga a barra horizontal
            
            processLogBox.add(textAreaScrollPane, BorderLayout.CENTER);
            logsPanel.add(processLogBox);
        }
        
        // Força o Swing a redesenhar o painel com os novos componentes
        logsPanel.revalidate();
        logsPanel.repaint();
    }  
    /**
     * NOVO: Limpa e recria os ComboBox e CheckBoxes.
     */
    private void atualizarControlesDinamicos(int numGenerais) {
        // Atualiza ComboBox do Comandante
        commanderComboBox.removeAllItems();
        for (int i = 0; i < numGenerais; i++) {
            commanderComboBox.addItem(i);
        }

        // Atualiza CheckBoxes dos Traidores
        traitorsPanel.removeAll();
        traitorCheckBoxes.clear();
        for (int id = 0; id < numGenerais; id++) {
            JCheckBox cb = new JCheckBox("ID " + id);
            cb.putClientProperty("processoId", id);
            traitorsPanel.add(cb);
            traitorCheckBoxes.add(cb);
        }
        
        traitorsPanel.revalidate();
        traitorsPanel.repaint();
    }
    
    /**
     * NOVO: Método auxiliar para habilitar/desabilitar os controles
     */
    private void setDynamicControlsEnabled(boolean enabled) {
        commanderComboBox.setEnabled(enabled);
        for (JCheckBox cb : traitorCheckBoxes) {
            cb.setEnabled(enabled);
        }
        // É preciso habilitar o painel pai também
        dynamicControlsPanel.setEnabled(enabled);
        traitorsPanel.setEnabled(enabled);
    }

    /**
     * Inicia a simulação.
     * (Modificado para desabilitar o 'setup')
     */
    private void iniciarSimulacao() {
        // 1. Limpa simulações anteriores
        encerrarSimulacao();
        logAreas.values().forEach(textArea -> textArea.setText(""));

        // 2. Pega as configurações da UI (agora dinâmicas)
        int commanderId = (Integer) commanderComboBox.getSelectedItem();
        List<Integer> traitors = new ArrayList<>();
        for (JCheckBox cb : traitorCheckBoxes) {
            if (cb.isSelected()) {
                traitors.add((Integer) cb.getClientProperty("processoId"));
            }
        }

        // 3. Cria os Processos (Generais)
        for (Integer id : processosConfig.keySet()) {
            boolean isTraidor = traitors.contains(id);
            JTextArea logArea = logAreas.get(id);
            
            Processo p = new Processo(id, commanderId, isTraidor, processosConfig, logArea, globalStepLock);
            generais.add(p);
        }

        // 4. Inicia as Threads
        for (Processo p : generais) {
            Thread t = new Thread(p::iniciar);
            processThreads.add(t);
            t.start();
        }

        // 5. Atualiza o estado da GUI para "em execução"
        startButton.setEnabled(false);
        nextStepButton.setEnabled(true);
        setDynamicControlsEnabled(false); // Trava a configuração
        setupButton.setEnabled(false); // Trava o setup
        processCountSpinner.setEnabled(false);
    }

    /**
     * Chamado pelo botão "Próxima Ação".
     * (Modificado para reabilitar os botões corretos no final)
     */
    private void executarProximoPasso() {
        synchronized (globalStepLock) {
            globalStepLock.notifyAll(); // Acorda todas as threads
        }
        
        // Verifica se a simulação terminou
        new Thread(() -> {
            try { Thread.sleep(500); } catch (InterruptedException e) {}
            
            boolean algumaThreadViva = processThreads.stream().anyMatch(Thread::isAlive);
            
            if (!algumaThreadViva) {
                // Simulação terminou! Reabilita os controles.
                SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(true); // Permite rodar de novo
                    nextStepButton.setEnabled(false);
                    setDynamicControlsEnabled(true); // Permite mudar traidores/comandante
                    setupButton.setEnabled(true); // Permite reconfigurar o número
                    processCountSpinner.setEnabled(true);
                });
            }
        }).start();
    }

    /**
     * Limpa os recursos da simulação anterior. (Sem alterações)
     */
    private void encerrarSimulacao() {
        for (Thread t : processThreads) {
            t.interrupt();
        }
        for (Processo p : generais) {
            p.desligar();
        }
        processThreads.clear();
        generais.clear();
    }
    
    // Método 'mostrarErro' (opcional, mas recomendado se você removeu antes)
    private void mostrarErro(String mensagem) {
        JFrame errorFrame = new JFrame("Erro");
        JTextArea textArea = new JTextArea(mensagem);
        errorFrame.add(textArea);
        errorFrame.pack();
        errorFrame.setLocationRelativeTo(null);
        errorFrame.setVisible(true);
    }

    /**
     * Método main para lançar a aplicação Swing. (Sem alterações)
     */
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.out.println("Não foi possível usar o Look&Feel do sistema.");
        }
        
        SwingUtilities.invokeLater(SimuladorGUI::new);
    }
}