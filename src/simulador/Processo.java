// Arquivo: simulador/Processo.java (Refatorado)
package simulador;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
// --- Imports de Swing ADICIONADOS ---
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class Processo {
    private final int id;
    private final int comandanteId;
    private final boolean isTraidor;
    private final Map<Integer, String[]> outrosProcessos;

    private final Comunicador comunicador;
    private final BlockingQueue<Mensagem> filaDeMensagens = new LinkedBlockingQueue<>();
    
    // --- Campo de GUI ADICIONADO ---
    private final JTextArea logArea; 
    
    // Campos de estado
    private String minhaOrdem = null;
    private final List<Mensagem> ordensRecebidas = new ArrayList<>();

    // --- CONSTRUTOR MODIFICADO ---
    // Recebe JTextArea para logar
    public Processo(int id, int comandanteId, boolean isTraidor, Map<Integer, String[]> outrosProcessos, JTextArea logArea) {
        this.id = id;
        this.comandanteId = comandanteId;
        this.isTraidor = isTraidor;
        this.outrosProcessos = outrosProcessos;
        this.logArea = logArea; // Salva a referência da GUI

        int minhaPorta = Integer.parseInt(outrosProcessos.get(id)[1]);
        this.comunicador = new Comunicador(minhaPorta, filaDeMensagens);
    }
    // --- FIM DO CONSTRUTOR ---

    /**
     * Ponto de entrada principal para a execução do processo.
     */
    public void iniciar() {
        log("Iniciado. Comandante: " + comandanteId + ". Traidor: " + isTraidor);
        comunicador.iniciarServidor();

        try {
            log("Servidor no ar. Aguardando 5s para outros processos iniciarem...");
            Thread.sleep(5000); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        aguardarProximoPasso("Iniciando Rodada 1...\n----------");

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
            // Tenentes recebem a ordem
            try {
                log("Sou um Tenente. Aguardando ordem do Comandante na Rodada 1.");
                Mensagem msgComandante = filaDeMensagens.take(); // Pega a ordem
                minhaOrdem = msgComandante.getOrdem();
                ordensRecebidas.add(msgComandante);
                log("Recebi do Comandante (" + msgComandante.getRemetenteId() + ") a ordem: '" + minhaOrdem + "'");
            } catch (InterruptedException e) {
                log("Interrompido enquanto esperava ordem do comandante.");
                Thread.currentThread().interrupt();
                return; // Termina
            }
        }

        // --- PAUSA OBSERVÁVEL ---
        aguardarProximoPasso("Rodada 1 concluída. Iniciando Rodada 2...\n----------");
        
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

            int numTenentes = outrosProcessos.size() - 2; 
            log("Aguardando " + numTenentes + " mensagens de outros tenentes.");
            try {
                for (int i = 0; i < numTenentes; i++) {
                    Mensagem msg = filaDeMensagens.take(); 
                    ordensRecebidas.add(msg);
                    log("Recebi de " + msg.getRemetenteId() + " a ordem: '" + msg.getOrdem() + "'");
                }
            } catch (InterruptedException e) {
                log("Interrompido enquanto esperava ordens dos tenentes.");
                Thread.currentThread().interrupt();
                return; 
            }
        }
        
        // --- PAUSA OBSERVÁVEL ---
        aguardarProximoPasso("Rodada 2 concluída. Iniciando Votação...\n----------");
        
        if (id != comandanteId) {
            log("Fim das rodadas. Iniciando votação.");
            decidirVotoMajoritario(ordensRecebidas);
        } else {
            log("Simulação concluída para o Comandante.");
        }
        
        log("PROCESSO CONCLUÍDO.");
        desligar();
    }
    
    /**
     * Pausa a simulação para torná-la observável.
     */
    private void aguardarProximoPasso(String motivo) {
        log(motivo);
        try {
            // Pausa a thread por 3 segundos
            Thread.sleep(3000); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
     * Loga diretamente na JTextArea da GUI (Thread-safe)
     */
    private void log(String message) {
        // Adiciona um prefixo para clareza
        String logCompleto = "[P" + id + "]: " + message;
        
        // Usa SwingUtilities para garantir que a atualização da GUI
        // aconteça na Thread de Eventos do Swing (EDT)
        SwingUtilities.invokeLater(() -> {
            logArea.append(logCompleto + "\n");
            // Auto-scroll para o final
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void desligar() {
        log("Servidor desligado. Encerrando.");
        comunicador.desligarServidor();
    }
    
    // --- O MÉTODO MAIN E LERCONFIGURACAO FORAM MOVIDOS PARA A PROCESSO_GUI.JAVA ---
}