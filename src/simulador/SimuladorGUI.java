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

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSlider; // --- NOVO IMPORT ---
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

public class SimuladorGUI {

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
    
    // Componentes de Setup
    private JSpinner processCountSpinner;
    private JButton setupButton;
    private JPanel dynamicControlsPanel;
    private JPanel traitorsPanel;
    private JPanel logsPanel;
    private JScrollPane logsScrollPane;
    
    // --- NOVOS COMPONENTES PARA FONTE ---
    private int currentFontSize = 12; // Tamanho padrão da fonte
    private JLabel fontLabel; // Rótulo para mostrar o tamanho
    // --- FIM DOS NOVOS COMPONENTES ---

    public SimuladorGUI() {
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

        // 1. Cria o painel de controle
        frame.add(criarPainelDeControle(), BorderLayout.NORTH);

        // 2. Cria o painel de logs (inicialmente vazio)
        logsPanel = new JPanel(new GridLayout(1, 0, 10, 10));
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

        // --- PAINEL DE SETUP (Número de Generais) ---
        JPanel setupPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        setupPanel.add(new JLabel("Número de Generais:"));
        
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
        traitorsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        traitorsBox.add(traitorsPanel);
        
        dynamicControlsPanel.add(commanderBox);
        dynamicControlsPanel.add(traitorsBox);
        mainPanel.add(dynamicControlsPanel);

        // --- PAINEL DE CONTROLE DE FONTE (NOVO) ---
        JPanel fontPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        fontLabel = new JLabel("Tamanho da Fonte: " + currentFontSize + "pt");
        fontPanel.add(fontLabel);
        
        JSlider fontSlider = new JSlider(JSlider.HORIZONTAL, 8, 24, currentFontSize);
        fontSlider.setMajorTickSpacing(4);
        fontSlider.setMinorTickSpacing(2);
        fontSlider.setPaintTicks(true);
        fontSlider.setPaintLabels(true);
        fontSlider.setPreferredSize(new Dimension(250, 45)); // Define um tamanho
        
        fontSlider.addChangeListener(e -> {
            JSlider source = (JSlider) e.getSource();
            // Atualiza o 'currentFontSize' e o rótulo
            currentFontSize = source.getValue();
            fontLabel.setText("Tamanho da Fonte: " + currentFontSize + "pt");
            
            // Só atualiza as fontes quando o usuário soltar o slider
            if (!source.getValueIsAdjusting()) {
                atualizarFontesDeLog(); // Chama o helper para atualizar JTextAreas existentes
            }
        });
        
        fontPanel.add(fontSlider);
        mainPanel.add(fontPanel); // Adiciona antes dos botões de ação

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
        setDynamicControlsEnabled(false);
        startButton.setEnabled(false);
        nextStepButton.setEnabled(false);
        
        return mainPanel;
    }

    /**
     * Chamado pelo botão "Configurar Simulação".
     */
    private void configurarSimulacao() {
        int numGenerais = (Integer) processCountSpinner.getValue();

        gerarConfiguracao(numGenerais);
        atualizarPainelDeLogs(numGenerais);
        atualizarControlesDinamicos(numGenerais);

        setDynamicControlsEnabled(true);
        startButton.setEnabled(true);
        nextStepButton.setEnabled(false);
    }
    
    /**
     * Gera o Map 'processosConfig' em memória.
     */
    private void gerarConfiguracao(int numGenerais) {
        processosConfig = new HashMap<>();
        for (int i = 0; i < numGenerais; i++) {
            processosConfig.put(i, new String[]{"localhost", String.valueOf(8000 + i)});
        }
    }

    /**
     * Limpa e recria o painel de logs.
     */
    private void atualizarPainelDeLogs(int numGenerais) {
        logsPanel.removeAll();
        logAreas.clear();
        
        logsPanel.setLayout(new GridLayout(1, numGenerais, 10, 10));

        for (int id = 0; id < numGenerais; id++) {
            JPanel processLogBox = new JPanel(new BorderLayout(5, 5));
            JLabel title = new JLabel("Processo " + id, JLabel.CENTER);
            title.setFont(new Font("System", Font.BOLD, 14));
            
            JTextArea logArea = new JTextArea(20, 25);
            logArea.setEditable(false);
            
            // --- MODIFICADO: Usa a variável 'currentFontSize' ---
            logArea.setFont(new Font("Monospaced", Font.PLAIN, currentFontSize));
            logArea.setBackground(Color.DARK_GRAY);
            logArea.setForeground(Color.LIGHT_GRAY);
            
            logArea.setLineWrap(true);
            logArea.setWrapStyleWord(true);
            
            logAreas.put(id, logArea);
            processLogBox.add(title, BorderLayout.NORTH);
            
            JScrollPane textAreaScrollPane = new JScrollPane(logArea);
            textAreaScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            textAreaScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            
            processLogBox.add(textAreaScrollPane, BorderLayout.CENTER);
            logsPanel.add(processLogBox);
        }
        
        logsPanel.revalidate();
        logsPanel.repaint();
    }  
    
    /**
     * Limpa e recria os ComboBox e CheckBoxes.
     */
    private void atualizarControlesDinamicos(int numGenerais) {
        commanderComboBox.removeAllItems();
        for (int i = 0; i < numGenerais; i++) {
            commanderComboBox.addItem(i);
        }

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
     * NOVO: Atualiza a fonte de todas as JTextAreas existentes.
     * Chamado pelo JSlider.
     */
    private void atualizarFontesDeLog() {
        Font newFont = new Font("Monospaced", Font.PLAIN, currentFontSize);
        for (JTextArea logArea : logAreas.values()) {
            logArea.setFont(newFont);
        }
    }
    
    /**
     * Método auxiliar para habilitar/desabilitar os controles
     */
    private void setDynamicControlsEnabled(boolean enabled) {
        commanderComboBox.setEnabled(enabled);
        for (JCheckBox cb : traitorCheckBoxes) {
            cb.setEnabled(enabled);
        }
        dynamicControlsPanel.setEnabled(enabled);
        traitorsPanel.setEnabled(enabled);
    }

    /**
     * Inicia a simulação.
     */
    private void iniciarSimulacao() {
        encerrarSimulacao();
        logAreas.values().forEach(textArea -> textArea.setText(""));

        int commanderId = (Integer) commanderComboBox.getSelectedItem();
        List<Integer> traitors = new ArrayList<>();
        for (JCheckBox cb : traitorCheckBoxes) {
            if (cb.isSelected()) {
                traitors.add((Integer) cb.getClientProperty("processoId"));
            }
        }

        for (Integer id : processosConfig.keySet()) {
            boolean isTraidor = traitors.contains(id);
            JTextArea logArea = logAreas.get(id);
            
            Processo p = new Processo(id, commanderId, isTraidor, processosConfig);
            generais.add(p);
        }

        for (Processo p : generais) {
            Thread t = new Thread(p::iniciar);
            processThreads.add(t);
            t.start();
        }

        startButton.setEnabled(false);
        nextStepButton.setEnabled(true);
        setDynamicControlsEnabled(false);
        setupButton.setEnabled(false);
        processCountSpinner.setEnabled(false);
    }

    /**
     * Chamado pelo botão "Próxima Ação".
     */
    private void executarProximoPasso() {
        synchronized (globalStepLock) {
            globalStepLock.notifyAll();
        }
        
        new Thread(() -> {
            try { Thread.sleep(500); } catch (InterruptedException e) {}
            
            boolean algumaThreadViva = processThreads.stream().anyMatch(Thread::isAlive);
            
            if (!algumaThreadViva) {
                SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(true);

                    nextStepButton.setEnabled(false);
                    setDynamicControlsEnabled(true);
                    setupButton.setEnabled(true);
                    processCountSpinner.setEnabled(true);
                });
            }
        }).start();
    }

    /**
     * Limpa os recursos da simulação anterior.
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
    
    /**
     * Método main para lançar a aplicação Swing.
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