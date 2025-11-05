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

public class Processo {
    private final int id;
    private final int comandanteId;
    private final boolean isTraidor;
    private final Map<Integer, String[]> outrosProcessos; // ID -> [host, porta]

    private final Comunicador comunicador;
    private final BlockingQueue<Mensagem> filaDeMensagens = new LinkedBlockingQueue<>();

    public Processo(int id, int comandanteId, boolean isTraidor, Map<Integer, String[]> outrosProcessos) {
        this.id = id;
        this.comandanteId = comandanteId;
        this.isTraidor = isTraidor;
        this.outrosProcessos = outrosProcessos;

        int minhaPorta = Integer.parseInt(outrosProcessos.get(id)[1]);
        this.comunicador = new Comunicador(minhaPorta, filaDeMensagens);
    }

    public void iniciar() throws InterruptedException {
        log("Iniciado. Comandante: " + comandanteId + ". Traidor: " + isTraidor);
        comunicador.iniciarServidor();

        // Pausa para garantir que todos os servidores estejam no ar antes de começar
        Thread.sleep(2000);

        String minhaOrdem = null;
        List<Mensagem> ordensRecebidas = new ArrayList<>();

        // --- RODADA 1: Comandante envia ordens ---
        if (id == comandanteId) {
            log("Sou o Comandante. Iniciando Rodada 1.");
            String ordemOriginal = "ATACAR";
            
            int i = 0;
            for (Map.Entry<Integer, String[]> entry : outrosProcessos.entrySet()) {
                if (entry.getKey() != id) {
                    String ordemParaEnviar = ordemOriginal;
                    if (isTraidor) {
                        // Comandante traidor envia ordens conflitantes
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
            // Tenentes recebem a ordem do comandante
            log("Sou um Tenente. Aguardando ordem do Comandante na Rodada 1.");
            Mensagem msgComandante = filaDeMensagens.take();
            minhaOrdem = msgComandante.getOrdem();
            ordensRecebidas.add(msgComandante);
            log("Recebi do Comandante (" + msgComandante.getRemetenteId() + ") a ordem: '" + minhaOrdem + "'");
        }

        Thread.sleep(1000); // Sincronização
        
        // --- RODADA 2: Tenentes retransmitem as ordens ---
        if (id != comandanteId) {
            log("Iniciando Rodada 2. Retransmitindo a ordem que recebi.");
            String ordemARetransmitir = minhaOrdem;
            if (isTraidor) {
                // Tenente traidor mente sobre a ordem que recebeu
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
            for (int i = 0; i < numTenentes; i++) {
                Mensagem msg = filaDeMensagens.take();
                ordensRecebidas.add(msg);
                log("Recebi de " + msg.getRemetenteId() + " a ordem: '" + msg.getOrdem() + "'");
            }

            // --- FASE DE DECISÃO ---
            log("Fim das rodadas. Iniciando votação.");
            decidirVotoMajoritario(ordensRecebidas);
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

        log("========================================");
        log("DECISÃO FINAL: " + decisaoFinal);
        log("========================================");
    }

    private void enviar(int idDestino, Mensagem msg) {
        String[] dest = outrosProcessos.get(idDestino);
        comunicador.enviarMensagem(dest[0], Integer.parseInt(dest[1]), msg);
    }
    
    private void log(String message) {
        System.out.println("[Processo " + id + "]: " + message);
    }

    public static void main(String[] args) throws FileNotFoundException, InterruptedException {
        if (args.length < 4) {
            System.out.println("Uso: java Processo <meu_id> <id_comandante> <eh_traidor> <arquivo_config>");
            return;
        }

        int meuId = Integer.parseInt(args[0]);
        int comandanteId = Integer.parseInt(args[1]);
        boolean isTraidor = Boolean.parseBoolean(args[2]);
        String configFile = args[3];

        Map<Integer, String[]> processos = new HashMap<>();
        try (Scanner scanner = new Scanner(new File(configFile))) {
            while (scanner.hasNextLine()) {
                String[] parts = scanner.nextLine().split(" ");
                processos.put(Integer.parseInt(parts[0]), new String[]{parts[1], parts[2]});
            }
        }
        
        new Processo(meuId, comandanteId, isTraidor, processos).iniciar();
    }
}
