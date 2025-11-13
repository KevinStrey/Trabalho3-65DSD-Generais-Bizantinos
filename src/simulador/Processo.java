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

// --- Imports de GUI removidos ---
// import javax.swing.JTextArea;
// import javax.swing.SwingUtilities;

public class Processo {
    private final int id;
    private final int comandanteId;
    private final boolean isTraidor;
    private final Map<Integer, String[]> outrosProcessos;

    private final Comunicador comunicador;
    private final BlockingQueue<Mensagem> filaDeMensagens = new LinkedBlockingQueue<>();

    // --- Campos de GUI removidos ---
    // private final JTextArea logArea; 
    // private final Object stepLock; 
    
    // Campos de estado
    private String minhaOrdem = null;
    private final List<Mensagem> ordensRecebidas = new ArrayList<>();

    // --- CONSTRUTOR MODIFICADO ---
    // Não recebe mais 'logArea' ou 'stepLock'
    public Processo(int id, int comandanteId, boolean isTraidor, Map<Integer, String[]> outrosProcessos) {
        this.id = id;
        this.comandanteId = comandanteId;
        this.isTraidor = isTraidor;
        this.outrosProcessos = outrosProcessos;

        int minhaPorta = Integer.parseInt(outrosProcessos.get(id)[1]);
        this.comunicador = new Comunicador(minhaPorta, filaDeMensagens);
    }
    // --- FIM DO CONSTRUTOR ---

    /**
     * Ponto de entrada principal para a execução do processo.
     * Este é o método que a nova 'main' irá chamar.
     */
    public void iniciar() {
        log("Iniciado. Comandante: " + comandanteId + ". Traidor: " + isTraidor);
        comunicador.iniciarServidor();

        // Pausa para garantir que todos os servidores estejam no ar
        try {
            // Este sleep é crucial em um sistema distribuído!
            // Dá tempo para todos os outros processos iniciarem seus servidores
            // antes que o comandante tente enviar mensagens.
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
                return; // Termina
            }
        }
        
        aguardarProximoPasso("Rodada 2 concluída. Iniciando Votação...\n----------");
        
        if (id != comandanteId) {
            log("Fim das rodadas. Iniciando votação.");
            decidirVotoMajoritario(ordensRecebidas);
        } else {
            log("Simulação concluída para o Comandante.");
        }
        
        log("PROCESSO CONCLUÍDO.");
        
        // Desliga o servidor interno
        desligar();
    }
    
    /**
     * O 'stepLock' foi removido. Este método agora apenas loga a etapa.
     */
    private void aguardarProximoPasso(String motivo) {
        log(motivo);
        // O stepLock.wait() foi removido. A simulação flui automaticamente.
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
        // dest[0] = IP (ex: 192.168.1.102), dest[1] = Porta (ex: 8001)
        comunicador.enviarMensagem(dest[0], Integer.parseInt(dest[1]), msg);
    }
    
    /**
     * Loga diretamente no Console (System.out)
     */
    private void log(String message) {
        // --- ALTERAÇÃO AQUI ---
        // Remove o prefixo para um log mais limpo
        System.out.println(message);
        // --- FIM DA ALTERAÇÃO ---
    }

    public void desligar() {
        comunicador.desligarServidor();
    }
    
    // -----------------------------------------------------------------
    // --- NOVO MÉTODO MAIN E LÓGICA DE CONFIGURAÇÃO ---
    // -----------------------------------------------------------------
    
    /**
     * Lê o arquivo de configuração e constrói o mapa de rede.
     */
    private static Map<Integer, String[]> lerConfiguracao(String nomeArquivo) throws FileNotFoundException {
        Map<Integer, String[]> mapaDeProcessos = new HashMap<>();
        File arquivo = new File(nomeArquivo);
        
        // Adiciona um log para ajudar a depurar onde ele procura o arquivo
        System.out.println("Lendo configuração de: " + arquivo.getAbsolutePath());
        
        Scanner scanner = new Scanner(arquivo);
        
        while (scanner.hasNextLine()) {
            String linha = scanner.nextLine().trim();
            if (linha.isEmpty() || linha.startsWith("#")) {
                continue; // Ignora linhas vazias ou comentários
            }
            
            String[] partes = linha.split("\\s+"); // Divide por espaços
            if (partes.length >= 3) {
                int id = Integer.parseInt(partes[0]);
                String ip = partes[1];
                String porta = partes[2];
                mapaDeProcessos.put(id, new String[]{ip, porta});
            }
        }
        scanner.close();
        return mapaDeProcessos;
    }

    /**
     * Ponto de entrada para executar o Processo como um programa independente.
     */
    public static void main(String[] args) {
        // Argumentos esperados:
        // args[0] = meuId (ex: "0")
        // args[1] = comandanteId (ex: "0")
        // args[2] = isTraidor (ex: "false" ou "true")
        
        if (args.length < 3) {
            System.err.println("Uso: java simulador.Processo <meuId> <comandanteId> <isTraidor>");
            System.err.println("Exemplo: java simulador.Processo 0 0 false");
            System.exit(1);
        }

        try {
            int meuId = Integer.parseInt(args[0]);
            int comandanteId = Integer.parseInt(args[1]);
            boolean isTraidor = Boolean.parseBoolean(args[2]);
            
            // Lê o arquivo de configuração
            Map<Integer, String[]> config = lerConfiguracao("config.txt");
            
            if (!config.containsKey(meuId)) {
                System.err.println("Erro: ID " + meuId + " não encontrado no config.txt!");
                System.exit((int) 1D);
            }
            
            // Cria e inicia o processo
            Processo p = new Processo(meuId, comandanteId, isTraidor, config);
            p.iniciar(); // Roda o processo na thread principal
            
        } catch (NumberFormatException e) {
            System.err.println("Erro: IDs devem ser números.");
            System.exit(1);
        } catch (FileNotFoundException e) {
            System.err.println("Erro: Arquivo 'config.txt' não foi encontrado.");
            System.err.println("Certifique-se de que ele está no mesmo diretório de onde você está executando o comando.");
            System.exit(1);
        }
    }
}