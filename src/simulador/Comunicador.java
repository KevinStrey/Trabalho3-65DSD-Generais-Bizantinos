package simulador;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;

/**
 * Gerencia a comunicação de rede (envio e recebimento de mensagens).
 */
public class Comunicador {
    private final int porta;
    private final BlockingQueue<Mensagem> filaDeMensagens;

    public Comunicador(int porta, BlockingQueue<Mensagem> filaDeMensagens) {
        this.porta = porta;
        this.filaDeMensagens = filaDeMensagens;
    }

    /**
     * Inicia uma thread para escutar por conexões e receber mensagens.
     */
    public void iniciarServidor() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(porta)) {
                // System.out.println("Servidor escutando na porta " + porta); // Log opcional
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> receberMensagem(clientSocket)).start();
                }
            } catch (IOException e) {
                System.err.println("Erro no servidor na porta " + porta + ": " + e.getMessage());
            }
        }).start();
    }

    /**
     * Lógica para receber um objeto Mensagem de um Socket.
     */
    private void receberMensagem(Socket socket) {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            Mensagem msg = (Mensagem) in.readObject();
            filaDeMensagens.put(msg); // Adiciona a mensagem na fila para ser processada
        } catch (IOException | ClassNotFoundException | InterruptedException e) {
            // Silencioso para não poluir o log com erros de conexão esperados
        }
    }

    /**
     * Envia uma mensagem para um host e porta específicos.
     */
    public void enviarMensagem(String host, int portaDestino, Mensagem msg) {
        try (Socket socket = new Socket(host, portaDestino);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            out.writeObject(msg);
        } catch (IOException e) {
            // System.err.println("Falha ao enviar para " + host + ":" + portaDestino); // Log opcional
        }
    }
}
