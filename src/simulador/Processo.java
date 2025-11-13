package simulador;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

// Importa o JTextArea para podermos logar diretamente nele
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class Processo {
    private final int id;
    private final int comandanteId;
    private final boolean isTraidor;
    private final Map<Integer, String[]> outrosProcessos;

    private final Comunicador comunicador;
    private final BlockingQueue<Mensagem> filaDeMensagens = new LinkedBlockingQueue<>();

    // --- NOVOS CAMPOS ---
    private final JTextArea logArea; // A área de texto da GUI para este processo
    private final Object stepLock; // O "sino" global que o botão "Próxima Ação" toca
    
    // Campos de estado que antes eram variáveis locais em iniciar()
    private String minhaOrdem = null;
    private final List<Mensagem> ordensRecebidas = new ArrayList<>();
    // --- FIM DOS NOVOS CAMPOS ---

    // --- CONSTRUTOR MODIFICADO ---
    public Processo(int id, int comandanteId, boolean isTraidor, Map<Integer, String[]> outrosProcessos,
                    JTextArea logArea, Object stepLock) {
        this.id = id;
        this.comandanteId = comandanteId;
        this.isTraidor = isTraidor;
        this.outrosProcessos = outrosProcessos;
        this.logArea = logArea; // Recebe da GUI
        this.stepLock = stepLock; // Recebe da GUI

        int minhaPorta = Integer.parseInt(outrosProcessos.get(id)[1]);
        this.comunicador = new Comunicador(minhaPorta, filaDeMensagens);
    }
    // --- FIM DO CONSTRUTOR ---

    /**
     * Este método agora é a lógica principal da Thread de cada processo.
     * Ele pausa em pontos chave esperando o 'stepLock'.
     */
    public void iniciar() {
        log("Iniciado. Comandante: " + comandanteId + ". \nTraidor: " + isTraidor);
        comunicador.iniciarServidor();

        // Pausa para garantir que todos os servidores estejam no ar antes de começar
        aguardarProximoPasso("Servidor no ar. Aguardando 'Próxima Ação' para Rodada 1..."
        		+ "\n----------");

        // --- RODADA 1: Comandante envia ordens ---
        if (id == comandanteId) {
            log("Sou o Comandante. Iniciando Rodada 1.");
            String ordemOriginal = "ATACAR";
            
            int i = 0;
            for (Map.Entry<Integer, String[]> entry : outrosProcessos.entrySet()) {
                if (entry.getKey() != id) {
                    String ordemParaEnviar = ordemOriginal;
                    if (isTraidor) {
                        ordemParaEnviar = (i % 2 == 0) ? "ATACAR" : "RECUAR";
                        log("TRAIÇÃO: Enviando ordem '" + ordemParaEnviar + "' para Processo " + entry.getKey());
                    } else {
                        log("Enviando ordem '" + ordemParaEnviar + "' para Processo " + entry.getKey());
                    }
                    enviar(entry.getKey(), new Mensagem(id, ordemParaEnviar));
                    i++;
                }
            }
        } else {
            // Tenentes recebem a ordem do comandante (o envio acima foi síncrono,
            // então a mensagem já deve estar na fila)
            try {
                log("Sou um Tenente. Aguardando ordem do Comandante na Rodada 1.");
                Mensagem msgComandante = filaDeMensagens.take(); // Pega a ordem
                minhaOrdem = msgComandante.getOrdem();
                ordensRecebidas.add(msgComandante);
                log("Recebi do Comandante (" + msgComandante.getRemetenteId() + ") a ordem: '" + minhaOrdem + "'");
            } catch (InterruptedException e) {
                log("Interrompido enquanto esperava ordem do comandante.");
                Thread.currentThread().interrupt();
                return; // Termina a thread
            }
        }

        // Sincronização
        aguardarProximoPasso("Rodada 1 concluída. Aguardando 'Próxima Ação' para Rodada 2..."
        		+ "\n----------");
        
        // --- RODADA 2: Tenentes retransmitem as ordens ---
        if (id != comandanteId) {
            log("Iniciando Rodada 2. Retransmitindo a ordem que recebi.");
            String ordemARetransmitir = minhaOrdem;
            if (isTraidor) {
                ordemARetransmitir = minhaOrdem.equals("ATACAR") ? "RECUAR" : "ATACAR";
                log("TRAIÇÃO: Recebi '" + minhaOrdem + "' mas vou retransmitir '" + ordemARetransmitir + "'");
            }

            for (Integer outroId : outrosProcessos.keySet()) {
                if (outroId != id && outroId != comandanteId) {
                    log("Enviando minha ordem ('" + ordemARetransmitir + "') para o Processo " + outroId);
                    enviar(outroId, new Mensagem(id, ordemARetransmitir));
                }
            }

            // Receber as ordens dos outros tenentes
            int numTenentes = outrosProcessos.size() - 2; // Exclui a si mesmo e o comandante
            log("Aguardando " + numTenentes + " mensagens de outros tenentes.");
            try {
                for (int i = 0; i < numTenentes; i++) {
                    Mensagem msg = filaDeMensagens.take(); // Pega as ordens
                    ordensRecebidas.add(msg);
                    log("Recebi de " + msg.getRemetenteId() + " a ordem: '" + msg.getOrdem() + "'");
                }
            } catch (InterruptedException e) {
                log("Interrompido enquanto esperava ordens dos tenentes.");
                Thread.currentThread().interrupt();
                return; // Termina a thread
            }
        }
        
        // --- FASE DE DECISÃO ---
        aguardarProximoPasso("Rodada 2 concluída. Aguardando 'Próxima Ação' para Votação..."
        		+ "\n----------");
        
        if (id != comandanteId) {
            log("Fim das rodadas. Iniciando votação.");
            decidirVotoMajoritario(ordensRecebidas);
        } else {
            log("Simulação concluída para o Comandante.");
        }
        
        log("PROCESSO CONCLUÍDO.");
    }
    
    /**
     * O ponto de pausa. O processo dorme aqui até que o 'stepLock' seja notificado.
     */
    private void aguardarProximoPasso(String motivo) {
        log(motivo);
        try {
            synchronized (stepLock) {
                stepLock.wait(); // Pausa a thread
            }
        } catch (InterruptedException e) {
            log("Thread interrompida. Encerrando.");
            Thread.currentThread().interrupt();
            // Se for interrompido, o método 'iniciar()' vai parar na próxima checagem
        }
    }
    
    private void decidirVotoMajoritario(List<Mensagem> ordens) {
        log("Ordens para decisão: " + ordens.stream().map(Mensagem::getOrdem).collect(Collectors.toList()));
        Map<String, Long> contagem = ordens.stream()
            .collect(Collectors.groupingBy(Mensagem::getOrdem, Collectors.counting()));
        
        String decisaoFinal = contagem.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("INCONCLUSIVO");

        log("===================================");
        log("DECISÃO FINAL: " + decisaoFinal);
        log("===================================");
    }

    private void enviar(int idDestino, Mensagem msg) {
        String[] dest = outrosProcessos.get(idDestino);
        comunicador.enviarMensagem(dest[0], Integer.parseInt(dest[1]), msg);
    }
    
    /**
     * Loga diretamente na JTextArea da GUI, sem o prefixo do processo.
     */
    private void log(String message) {
        // --- ALTERAÇÃO AQUI ---
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n"); // Removeu o prefixo "[Processo " + id + "]: "
            logArea.setCaretPosition(logArea.getDocument().getLength()); // Auto-scroll
        });
        // --- FIM DA ALTERAÇÃO ---
    }

    /**
     * Novo método para a GUI chamar e limpar os recursos.
     */
    public void desligar() {
        comunicador.desligarServidor();
    }
}