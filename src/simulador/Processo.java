package simulador;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class Processo {
    private final int id;
    private final int comandanteId;
    private final boolean isTraidor;
    private final Map<Integer, String[]> outrosProcessos;
    private final Comunicador comunicador;
    private final BlockingQueue<Mensagem> filaDeMensagens = new LinkedBlockingQueue<>();

    private String ordemDoComandante = null; // O que o comandante ME disse
    private final Map<Integer, String> ordemQueCadaTenenteRecebeuDoComandante = new HashMap<>();
    private JTextArea logArea;

    // Valor padrão para caso de ausência de mensagem ou empate (Requisito BFT)
    private static final String VALOR_PADRAO = "RECUAR";

    public Processo(int id, int comandanteId, boolean isTraidor, Map<Integer, String[]> outrosProcessos, JTextArea logArea) {
        this.id = id;
        this.comandanteId = comandanteId;
        this.isTraidor = isTraidor;
        this.outrosProcessos = outrosProcessos;
        this.logArea = logArea;

        int minhaPorta = Integer.parseInt(outrosProcessos.get(id)[1]);
        this.comunicador = new Comunicador(minhaPorta, filaDeMensagens);
    }

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
            // Ordenar chaves para garantir determinismo na distribuição de traição
            List<Integer> destinos = new ArrayList<>(outrosProcessos.keySet());
            Collections.sort(destinos);

            for (Integer destinoId : destinos) {
                if (destinoId != id) {
                    String ordemParaEnviar = ordemOriginal;
                    if (isTraidor) {
                        // Traidor alterna as ordens para confundir os tenentes
                        ordemParaEnviar = (i % 2 == 0) ? "ATACAR" : "RECUAR";
                        log("TRAIÇÃO: Enviando ordem '" + ordemParaEnviar + "' para Processo " + destinoId);
                    } else {
                        log("Enviando ordem '" + ordemParaEnviar + "' para Processo " + destinoId);
                    }
                    enviar(destinoId, new Mensagem(id, ordemParaEnviar));
                    i++;
                }
            }
            log("Comandante finalizou envio de ordens.");
        } else {
            // Tenentes recebem a ordem do comandante
            try {
                log("Sou um Tenente. Aguardando ordem do Comandante na Rodada 1.");
                Mensagem msgComandante = filaDeMensagens.take();
                // Verifica se a mensagem veio realmente do comandante
                if (msgComandante.getRemetenteId() == comandanteId) {
                    ordemDoComandante = msgComandante.getOrdem();
                    log("Recebi do Comandante (" + msgComandante.getRemetenteId() + ") a ordem: '" + ordemDoComandante + "'");
                } else {
                    log("ERRO: Recebi mensagem na rodada 1 de alguém que não é o comandante!");
                }
            } catch (InterruptedException e) {
                log("Interrompido enquanto esperava ordem do comandante.");
                Thread.currentThread().interrupt();
                return;
            }
        }

        aguardarProximoPasso("Rodada 1 concluída. Iniciando Rodada 2...\n----------");
        
        // --- RODADA 2: Tenentes retransmitem o que o COMANDANTE lhes disse ---
        if (id != comandanteId) {
            log("Iniciando Rodada 2. Retransmitindo o que o COMANDANTE me disse.");
            
            String ordemARetransmitir = (ordemDoComandante != null) ? ordemDoComandante : VALOR_PADRAO;
            
            if (isTraidor) {
                // Se EU sou traidor, minto sobre o que o comandante disse
                ordemARetransmitir = ordemARetransmitir.equals("ATACAR") ? "RECUAR" : "ATACAR";
                log("TRAIÇÃO: O Comandante me disse '" + ordemDoComandante + "' mas vou dizer que disse '" + ordemARetransmitir + "'");
            } else {
                log("Sendo honesto: vou dizer que o Comandante me disse '" + ordemARetransmitir + "'");
            }

            // Envia para todos os outros tenentes (excluindo Comandante e eu mesmo)
            for (Integer outroId : outrosProcessos.keySet()) {
                if (outroId != id && outroId != comandanteId) {
                    log("Dizendo ao Processo " + outroId + ": 'O Comandante me disse " + ordemARetransmitir + "'");
                    enviar(outroId, new Mensagem(id, ordemARetransmitir));
                }
            }

            // Aguarda as retransmissões dos outros tenentes
            int numTenentes = outrosProcessos.size() - 2; // Total menos (Comandante + Eu)
            log("Aguardando " + numTenentes + " mensagens de outros tenentes.");
            
            try {
                for (int i = 0; i < numTenentes; i++) {
                    Mensagem msg = filaDeMensagens.take(); 
                    ordemQueCadaTenenteRecebeuDoComandante.put(msg.getRemetenteId(), msg.getOrdem());
                    log("Tenente " + msg.getRemetenteId() + " diz que o Comandante lhe disse: '" + msg.getOrdem() + "'");
                }
            } catch (InterruptedException e) {
                log("Interrompido enquanto esperava ordens dos tenentes.");
                Thread.currentThread().interrupt();
                return; 
            }
        }
        
        aguardarProximoPasso("Rodada 2 concluída. Iniciando Votação...\n----------");
        
        if (id != comandanteId) {
            log("Fim das rodadas. Iniciando votação.");
            decidirVotoMajoritario();
        } else {
            log("Simulação concluída para o Comandante.");
        }
        
        log("PROCESSO CONCLUÍDO.");
        desligar();
    }

    private void aguardarProximoPasso(String motivo) {
        log(motivo);
        try {
            Thread.sleep(3000); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Algoritmo OM(1) Corrigido.
     * * O vetor de decisão deve considerar apenas as informações detidas pelos TENENTES.
     * Não se deve incluir o Comandante explicitamente como um "voto" extra no vetor, 
     * pois o valor do Comandante para mim já está representado pelo "meu valor".
     * Incluí-lo novamente cria um peso duplo para a informação local, impedindo consenso
     * quando o comandante é traidor.
     */
    private void decidirVotoMajoritario() {
        // Lista ordenada de todos os participantes
        List<Integer> participantes = new ArrayList<>(outrosProcessos.keySet());
        Collections.sort(participantes);

        // Construir vetor: para cada processo TENENTE, qual ordem ele recebeu/reportou
        Map<Integer, String> vetorDeValores = new HashMap<>();
        log("Construindo vetor de consenso (apenas Tenentes):");
        
        for (Integer pid : participantes) {
            // CORREÇÃO: O Comandante não participa do vetor de votação dos Tenentes.
            // Os tenentes estão tentando concordar sobre a ordem do comandante.
            if (pid == comandanteId) {
                continue;
            }

            String valor;
            if (pid == id) {
                // Minha própria informação direta do comandante
                valor = (ordemDoComandante != null) ? ordemDoComandante : VALOR_PADRAO;
                log("  [Tenente " + pid + " (eu)] = '" + valor + "' (direto do Comandante)");
            } else {
                // O que outro tenente reportou
                if (ordemQueCadaTenenteRecebeuDoComandante.containsKey(pid)) {
                    valor = ordemQueCadaTenenteRecebeuDoComandante.get(pid);
                    log("  [Tenente " + pid + "] = '" + valor + "' (repassado)");
                } else {
                    valor = VALOR_PADRAO;
                    log("  [Tenente " + pid + "] = '" + valor + "' (padrão - sem info)");
                }
            }
            
            vetorDeValores.put(pid, valor);
        }

        // Construir lista ordenada de valores para exibição
        List<String> vetorOrdenado = new ArrayList<>();
        // Reitera sobre participantes apenas para manter a ordem visual correta no log
        for (Integer pid : participantes) {
            if (vetorDeValores.containsKey(pid)) {
                vetorOrdenado.add(vetorDeValores.get(pid));
            }
        }
        log("Vetor final de votos: " + vetorOrdenado);

        // Contar votos
        Map<String, Integer> contagem = new HashMap<>();
        for (String v : vetorDeValores.values()) {
            contagem.put(v, contagem.getOrDefault(v, 0) + 1);
        }

        log("Contagem: " + contagem);

        int totalVotos = vetorDeValores.size();
        // Maioria simples necessária
        int maioriaNecessaria = (totalVotos / 2) + 1;
        log("Votos válidos: " + totalVotos + ", Maioria necessária: " + maioriaNecessaria);

        // Encontrar o valor com mais votos
        String decisaoFinal = null;
        int maxVotos = 0;
        
        List<String> opcoes = new ArrayList<>(contagem.keySet());
        Collections.sort(opcoes); // Garante desempate determinístico (alfabético: ATACAR < RECUAR)
        
        // Verifica se há maioria
        for (String opcao : opcoes) {
            int votos = contagem.get(opcao);
            if (votos > maxVotos) {
                maxVotos = votos;
                decisaoFinal = opcao;
            }
        }

        // Lógica de Consenso
        if (maxVotos >= maioriaNecessaria) {
            log(">>> CONSENSO (Maioria): '" + decisaoFinal + "' com " + maxVotos + "/" + totalVotos + " votos");
        } else {
            // Em caso de empate exato (ex: 2 ATACAR, 2 RECUAR), usa-se o determinismo
            // Como ordenamos 'opcoes' alfabeticamente, pegamos o primeiro com maxVotos
             for (String opcao : opcoes) {
                if (contagem.get(opcao) == maxVotos) {
                    decisaoFinal = opcao;
                    break;
                }
            }
            log(">>> CONSENSO (Empate/Desempate Determinístico): '" + decisaoFinal + "'");
        }

        log("===================================");
        log("DECISÃO FINAL DO GENERAL " + id + ": " + decisaoFinal);
        log("===================================");
    }

    private void enviar(int idDestino, Mensagem msg) {
        String[] dest = outrosProcessos.get(idDestino);
        if (dest != null) {
            comunicador.enviarMensagem(dest[0], Integer.parseInt(dest[1]), msg);
        } else {
            log("Erro: Não encontrei config para ID " + idDestino);
        }
    }
    
    private void log(String message) {
        String logCompleto = "[P" + id + "]: " + message;
        SwingUtilities.invokeLater(() -> {
            logArea.append(logCompleto + "\n");
            try {
                logArea.setCaretPosition(logArea.getDocument().getLength());
            } catch (Exception e) {
                // Ignorar erro de UI pontual
            }
        });
    }

    public void desligar() {
        log("Servidor desligado. Encerrando.");
        comunicador.desligarServidor();
    }
}